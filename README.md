# Torpedo

Lets you torpedo complex functional expressions.

## Usage

Torpedo isn't hosted in any public Maven repositories, so you'll need to build and install it to
your local Maven repo:

```
$ git clone git://github.com/Factual/torpedo
$ cd torpedo
$ lein install
```

The project dependency should be this:

```clojure
[torpedo "0.1.0-SNAPSHOT"]
```

Then you can import the torpedo macros, which are `>>>` and `>>>>`. `>>>` is used on expressions and
`>>>>` on blocks of code (this distinction is described in more detail below).

```clojure
(ns my.namespace
  (:use [torpedo :only [>>> >>>>]))
```

## Motivational example

```clojure
; without torpedo:
(defn sqr [x] (* x x))
(defn average
  [xs] (/ (double (reduce + xs)) (count xs)))
(defn variance
  [xs] (- (average (map sqr xs))
          (sqr (average xs))))
(defn random-generator
  [lower upper]
  #(+ lower (rand (- upper lower))))

; with torpedo:
(>>>>
 (def (sqr x) (* x x))
 (def average @(/ double..reduce:+ count))
 (def variance @(- average..map:sqr sqr.average))
 (def ((random-generator lower upper))
      (+:lower..rand.- upper lower)))   ; ok, this might be demotivational...
```

## Examples

Torpedo supports three main features to help make your code more compact:

### Feature 1: Composition and partial syntax

If you write a symbol that contains `.` or `:`, Torpedo will turn it into a composition or partial
application. For example:

```clojure
(>>> (first.rest [1 2 3]))   ; ((comp first rest) [1 2 3])
(def sum (>>> reduce:+))     ; (def sum (partial reduce +))
```

Composition takes higher precedence than partial application. However, you can append a dot to any
operation to lower its precedence. For instance:

```clojure
(def incsum (>>> inc..reduce:+))
(def mapinc (>>> map:.+:'1))     ; see below for explanation of '1
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

Sometimes you want to use an actual number instead of using it to select one of the arguments. In
cases like this, you can quote it:

```clojure
(defn add-three-to
  [xs]
  (map (partial + 3) xs))

(def add-three-to (>>> map:.+:'3))

; Note that this is not the same as using two regular colons:
; (>>> map:+:'3) produces (partial map + 3) -- which won't work at all.
```

Quoting a number produces a numeric literal. Quoting anything else produces a `(quote x)` in your
code. This can be useful in cases such as:

```clojure
(def all-but-a (>>> filter:.not=:'a))
(all-but-a '[a b c d e])        ; (b c d e)
```

### Feature 2: Function lifting

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

Function lifting works the same way for functions and macros. For example:

```clojure
(def vectorize
  (>>> @(if vector? identity vector)))
(vectorize 5)    ; [5]
(vectorize [5])  ; [5]
```

Like the symbol transformer, `@` allows you to quote subexpressions if you want them to be left
alone:

```clojure
(def x 5)
(def add5-to-first (>>> @(+ 'x first)))
(add5-to-first [1 2 3])  ; 6
```

### Feature 3: Bindings

You'll notice that so far all of our invocations of `>>>` have involved just one argument. If you
specify more, it will construct a `let`-binding using the remaining forms. For example:

```clojure
(>>> sum
     sum (+ x 10)
     x   5)  ; 15

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

You can build curried functions this way:

```clojure
(def add-5-to (>>> (add 5)
                   ((add x) y) (+ x y)))
(add-5-to 6)  ; 11
```

Torpedo names functions that you bind using applicative notation. This is mostly useful for
recursion; for instance:

```clojure
(>>> (factorial 5)
     (factorial n) (if (> n 1) (* n (factorial.dec n))
                               1))  ; 120
```

This also works if you're defining a curried function. In this case, you can specify which level of
closure you want by suffixing the function name with an apostrophe:

```clojure
(>>> ((partial-fact 3) 5)
     ((partial-fact b) n) (if (> n b)
                            (* n (partial-fact' (dec n)))
                            b))  ; 60
```

One apostrophe indicates that you want to close over one layer of parameters; so you can reuse the
outer closure over `b`.

## Alternative forms

### Transforming blocks of code

All of the examples above use the `>>>` macro, which transforms single expressions. But you can also
use Torpedo to transform toplevel definitions. To do this, you just wrap a piece of code in `>>>>`:

```clojure
(>>>>
 (defn sqr [x] (* x x))
 (def average @(/ double..reduce:+ count))
 (def variance @(- average..map:sqr sqr.average)))

(variance [1 2 3 4 5 6])  ; 2.9166666
```

You can nest invocations of `>>>` if you want to use it to bind variables. For example:

```clojure
(>>>>
 (def some-input 10)
 (def some-output (>>> (g some-input)
                       (g x) (* x (f x))
                       f     +:'1)))
some-output  ; 110
```

Quoted subexpressions work correctly if you do this; you won't need to double-quote something just
because it's inside two layers of macros. (Internally, the outer macro macroexpands the inner one
and leaves the result untouched.)

### `def` rewriting

Torpedo also does binding rewriting specifically for `def` (though not `defn` or other definition
forms, since these expect formals):

```clojure
(>>>>
 (def (f x) (+ x 1)))
(f 5)  ; 6
```

The behavior of this form is identical to the bindings in `>>>`; that is, you can use currying,
intermediate closure functions are named, etc. The only difference is that `def` still defines just
one form at a time, whereas `>>>` lets you define arbitrarily many.

## Caveats

There are two big things to watch out for when using this library. First, know that it fails
horribly if you try to use composition/partial syntax in conjunction with explicit namespaces. For
example:

```clojure
(ns ...
 (:require [clojure.string :as s]))
(>>>>
 (def separator " ")
 (def joiner s/join:separator))  ; blows up at compile time: "join" not found
```

Torpedo removes the namespace from every symbol it sees; the somewhat lame workaround is to quote
these symbols to prevent Torpedo from transforming them:

```clojure
(>>>>
 (def join-fn 's/join)
 (def separator " ")
 (def joiner join-fn:separator))  ; this works
```

Fixing this would make the expansion rules ambiguous in limit cases, and at the very least subject
to runtime bindings, so this will probably remain a failure case for the foreseeable future.

The other big caveat is quoting. If you quote something in Torpedo, you're telling it to leave that
form alone; but in the process Torpedo removes the quotation around that form. So, for example:

```clojure
(>>> (first '[a b c]))   ; croaks with "unbound symbol 'a"
(>>> (first ''[a b c]))  ; you want to say this
```

Notice that it doesn't matter whether Torpedo would have changed the form; one layer of quotation is
always removed. (The only exception is when it would have been unambiguous, which happens inside the
symbol rewriter. `filter:.not=:'a`, for example, is already being transformed, so the quotation on
`'a` can only mean that you actually want to quote it.)

# License

The use and distribution terms for this software are covered by the
Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
which can be found in the file LICENSE.html at the root of this distribution.
By using this software in any fashion, you are agreeing to be bound by
the terms of this license.
You must not remove this notice, or any other, from this software.
