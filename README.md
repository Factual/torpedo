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
(>>> (first.rest [1 2 3]))   -> ((comp first rest) [1 2 3])
(def sum (>>> reduce:+))     -> (def sum (partial reduce +))
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

### Function lifting

You can prepend subexpressions with `@` to lift them into functional mode. This works for maps, sets,
vectors, and lists. For maps, sets, and vectors, it transposes function application across each
element. For example:

```clojure
(>>> (@[first second] [1 2 3]))    -> [1 2]
(>>> (@#{min max} 1 2 3))          -> #{1 3}
(>>> (@{:min min :max max} 1 5 8)) -> {:min 1, :max 8}
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
(first-and-last [1 2 3 4])   -> [1 4]
```

And you can use them with the composition and partial syntax:

```clojure
(defn average
  [xs]
  (/ (reduce + xs) (count xs)))

(def average
  (>>> @(/ reduce:+ count)))
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
        sum (+ x 10)]
    sum)
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

## License

Copyright (C) 2012 Spencer Tipping
Distributed under the MIT source code license
