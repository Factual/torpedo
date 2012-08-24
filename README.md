# Torpedo

Lets you torpedo complex functional expressions.

## Usage

```clojure
(ns my.namespace
  (:use [torpedo :only [>>>]))
```

## Examples

Torpedo supports three main features to help make your code more compact:

### Composition and partial syntax

If you write a symbol that contains `.` or `:`, Torpedo will turn it into a composition or partial
application. For example:

```clojure
(>>> (first.rest [1 2 3]))   ; ((comp first rest) [1 2 3])
(def sum (>>> reduce:+))     ; (def sum (partial reduce +))
```

Composition takes higher precedence than partial application. However, you can write two consecutive
dots to indicate that you want a low-precedence composition. For instance:

```clojure
(def incsum (>>> inc..reduce:+))
```

You can quote a symbol to preserve it literally:

```clojure
(def literally-foo.bar (>>> 'foo.bar))
(def the-quoted-symbol-q (>>> ''q))
```

You can also compose with numbers to select individual arguments. For example:

```clojure
(def count-the-first (>>> count.0))
(count-the-first [1 2 3] [4 5])     ; 3

(def count-the-last (>>> count.-1))
(count-the-last [1 2 3] [4 5] [6])  ; 1
(count-the-last [1 2 3])            ; 3
```

### Function lifting

You can prepend subexpressions with `@` to lift them into functional mode. This works for maps,
sets, vectors, and lists. For maps, sets, and vectors, it transposes function application across the
elements in the container. (Note that map keys are also transformed!) For example:

```clojure
(>>> (@[first second] [1 2 3]))      ; [1 2]
(>>> (@#{min max} 1 2 3))            ; #{1 3}
(>>> (@{':min min ':max max} 1 5 8)) ; {:min 1, :max 8}
```

Notice from the `min` and `max` examples that all outer arguments are forwarded to the functions
inside the data structures. You can use number composition to select a particular argument:

```clojure
(>>> (@{':first count.0 ':last count.-1} [1 2 3] [4 5] [6])) ; {:first 3, :last 1}
```

Lists are handled in a more interesting way. Every list is assumed to be a function application.
Lifting a function application means treating each of the arguments as a function. So, for instance,
suppose we wanted to return the sum of the first and last elements in a vector. Here are two
equivalent ways to write it:

```clojure
(defn sum-first-last
  [v]
  (+ (first v) (last v)))

(def sum-first-last (>>> @(+ first last)))
; expands into:
; (def sum-first-last
;   (fn [& args]
;     (+ (apply first args) (apply last args))))
```

You can combine these transformations:

```clojure
(def first-and-last
  (>>> @(conj [first] last)))
(first-and-last [1 2 3 4])   ; [1 4]
```

And you can use them with the composition and partial syntax:

```clojure
(defn average
  [xs]
  (/ (reduce + xs) (count xs)))

(def average
  (>>> @(/ reduce:+ count)))
```

Like the symbol transformer, `@` allows you to quote subexpressions if you want them to be left
alone:

```clojure
(def x 5)
(def add5-to-first (>>> @(+ 'x first)))
(add5-to-first [1 2 3])  ; 6
```

### Bindings

You'll notice that we've got this `>>>` macro, but it only takes one argument. If you specify more,
it will construct a `let`-binding using the remaining forms. For example:

```clojure
(>>> sum
     sum (+ x 10)
     x   5)
; expands into:
; (let [x 5
;       sum (+ x 10)]
;   sum)
```

Unlike `let`, `>>>` expects bindings to _precede_, not _follow_, their dependencies.

Also unlike `let`, `>>>` allows you to bind functions without using anonymous function literals:

```clojure
(>>> (sum [1 2 3 4 5])
     (sum xs) (reduce + xs))
; expands into:
; (let [sum (fn sum [xs] (reduce + xs))]
;   (sum [1 2 3 4 5]))
```

You can also build curried functions this way:

```clojure
(>>> (add 5)
     ((add x) y) (+ x y))
```

# License

The use and distribution terms for this software are covered by the
Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
which can be found in the file LICENSE.html at the root of this distribution.
By using this software in any fashion, you are agreeing to be bound by
the terms of this license.
You must not remove this notice, or any other, from this software.
