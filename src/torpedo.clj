(ns torpedo
  "
  Torpedo is a DSL that allows you to bind variables after you use them. It also supports a bunch of
  nice shorthands for working with and binding functions.
  "
  (:require [clojure.string :as s]))

(defn preorder
  "
  Preorder tree transform. Internals were taken from clojure.walk/prewalk, but this version does not
  rewrite transform outputs (this was a mistake in clojure.walk IMO).
  "
  [f form]
  (let [form' (f form)
        each  (partial preorder f)]
    (if (identical? form' form)
      (cond (list? form)                            (apply list (map each form))
            (instance? clojure.lang.IMapEntry form) (vec (map each form))
            (seq? form)                             (map each form)
            (coll? form)                            (into (empty form) (map each form))
            :else                                   form)
      form')))

(def rewrite)
(defn rewrite-bindings
  "
  Given a series of binding pairs (just like those that would be passed to let[]), we look for any
  lvalues that appear to be invocations and wrap the corresponding right-hand sides in anonymous
  functions. For example:

  (f x) (+ x 1)   -> f (fn f [x] (+ x 1))

  If the function part of the left-hand side is a symbol, we use that as the name of the anonymous
  function (so that recursion works). Otherwise, we dig through the lists and append an apostrophe
  for each layer:

  (((f x) y) z) (+ x y z)  -> f (fn f [x] (fn f' [y] (fn f'' [z] (+ x y z))))
  "
  [binding-vector]
  (let [name-of (fn name-of [form] (if (symbol? (first form))
                                     (first form)
                                     (symbol (str (name-of (first form)) "'"))))

        expand-fn (fn [lhs rhs] `(fn ~(name-of lhs) [~@(next lhs)] ~rhs))
        expand-all (fn expand-all [[lhs rhs]] (if (list? lhs)
                                                (recur [(first lhs) (expand-fn lhs rhs)])
                                                [lhs (rewrite rhs)]))]
    (vec (mapcat expand-all (reverse (partition 2 binding-vector))))))

(defn rewrite-symbol
  "
  Rewrites the following syntax within symbols, regardless of their position within expressions:

  3             -> (fn [& args] (nth args 3))
  -3            -> (fn [& args] (nth args (+ (count args) -3)))
  '3            -> 3
  'x            -> (quote x)
  f.g.h...      -> (comp f g h ...)
  f:x:y:...     -> (partial f x y ...)
  f..g..h....   -> (comp f g h ...) but lower precedence
  f:.x:.y....   -> (partial f x y ...) but lower precedence

  These are listed in descending order of precedence. Note that the literal numeric syntax isn't a
  feature built for FP as much as it is a convenience for argument preloading:

  (map +:'3 xs) -> (map (partial + 3) xs)

  Note that you can't specify floating point numbers this way, as that would make the syntax
  ambiguous. By extension:

  (filter =:'a xs) -> (filter (partial = 'a) xs)
  "
  [sym]
  (let [promote #(cond (re-matches #"\d+" %)    `(fn [& xs#] (nth xs# ~(Integer/parseInt %)))
                       (re-matches #"-\d+" %)   `(fn [& xs#] (nth xs# (+ (count xs#)
                                                                         ~(Integer/parseInt %))))
                       (re-matches #"'-?\d+" %) (Integer/parseInt (subs % 1))
                       (re-matches #"'.*" %)    `(quote ~(symbol (subs % 1)))
                       :else                    (symbol %))

        prefixed-map (fn [prefix f] #(if (next %)
                                       (cons prefix (map f %))
                                       (f (first %))))

        inner-comp     (prefixed-map 'comp    promote)
        inner-partials (prefixed-map 'partial #(inner-comp     (s/split % #"\.")))
        outer-comp     (prefixed-map 'comp    #(inner-partials (s/split % #":")))
        outer-partials (prefixed-map 'partial #(outer-comp     (s/split % #"\.\.")))]
    (outer-partials (s/split (name sym) #":\."))))

(defn apply-value
  "
  Rewrites a function value to apply it to a parameter list. Generally this just means wrapping it
  with (apply), but in some cases we can do a little better. For instance, if we observe a partial
  function, we can un-partial it for better performance:

  (apply-value '(partial f x) 'y) -> '(apply f x y)
  (apply-value '(fn [& argsX] ...) 'y) -> '(let [argsX y] ...)
  "
  [value args]
  (or (and (seq? value)
           (case (first value) partial `(apply ~@(rest value) ~args)
                               fn      (when (= '& (first (second value)))
                                         `(let ~(vec (conj (vec (rest (second value))) args))
                                            ~@(nnext value)))
                               nil))
      `(apply ~value ~args)))

(defn rewrite-lift
  "
  Lifts applicativity across the given value. The exact transformation is this (with lifting denoted
  as @):

  @[a b ...]   = (fn [& args] [(apply @a args) (apply @b args) ...])
  @{k1 v1 ...} = (fn [& args] {(apply @k1 args) (apply @v1 args) ...})
  @#{x y ...}  = (fn [& args] #{(apply @x args) (apply @y args) ...})
  @(f x y ...) = (fn [& args] (f (apply @x args) (apply @y args) ...))
  @'x          = x

  The rewriter optimizes a few cases; this process is documented in apply-value.
  "
  [form & [args-name]]

  (let [args (gensym "args")
        wrap #(let [is-deref? (and (list? %) (= 'clojure.core/deref (first %)))]
                (cond is-deref? (second %)
                      args-name (apply-value % args-name)
                      :else     %))]

    (cond (symbol? form) (wrap (rewrite-symbol form))
          (vector? form) (wrap `(fn [& ~args] ~(vec (map #(rewrite-lift % args) form))))
          (set?    form) (wrap `(fn [& ~args] ~(into #{} (map #(rewrite-lift % args) form))))
          (map?    form) (wrap `(fn [& ~args] ~(into {}  (map (comp vec (partial map
                                                                         #(rewrite-lift % args)))
                                                              (seq form)))))
          (seq?    form) (if (= 'quote (first form))
                           (second form)
                           (wrap `(fn [& ~args]
                                    (~(first form) ~@(map #(rewrite-lift % args) (rest form))))))
          :else          form)))

(defn rewrite
  "
  Expands a value according to a few different rules. We assume default context in this function, so
  we are not doing any special rewriting until we see a form that requires it. In default context,
  these forms are:

  some-symbol   -> see docs for rewrite-symbol
  'x            -> x
  @...          -> (fn [& args] ~(rewrite-lift ...))

  We eagerly macroexpand sub-occurrences of >>>.
  "
  [form]
  (preorder #(cond (symbol? %) (rewrite-symbol %)
                   (seq? %)    (case (first %) clojure.core/deref (rewrite-lift (second %))
                                               quote              (second %)
                                               def                `(def ~@(rewrite-bindings
                                                                           (rest %)))
                                               >>>                (macroexpand-1 %)
                                               >>>>               (macroexpand-1 %)
                                                                  %)
                   :else %)
             form))

(defmacro >>>
  "
  Returns a value with optional bindings afterwards. Each binding must *precede* the things it
  depends on! This is the opposite of (let[]), which requires bindings to follow dependencies. For
  example:

  (>>> (f y)
       (f x) (+ x 1)    <- turns into f (fn f [x] (+ x 1))
       y     5)

  Both the expression and the bindings are subject to certain expansions, described in some detail
  in the README for this project. Left-hand sides of the bindings are minimally preprocessed to
  support streamlined function definitions.
  "
  [expression & bindings]
  (if bindings
    `(let ~(rewrite-bindings bindings)
       ~(rewrite expression))
    (rewrite expression)))

(defmacro >>>>
  "
  Returns the value of a block of code. This macro doesn't provide a way to bind values directly;
  for that, you should use >>>.
  "
  [& forms]
  `(do ~@(map rewrite forms)))
