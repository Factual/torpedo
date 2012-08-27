(ns torpedo.test
  (:use [torpedo] :reload)
  (:use [clojure.test])
  (:require [clojure.string :as s]))

(deftest symbol-rewriting
  (are [form args result] (= ((>>> form) args) result)
       first.rest       [1 2 3] 2
       reduce:+         [1 2 3] 6
       map:inc          [1 2 3] [2 3 4]
       inc.inc.inc      3       6
       inc..reduce:*    [1 2 3] 7
       list.map:inc     [1 2]   '((2 3))
       map:inc.inc      [1 2]   '(3 4)
       map:inc..reverse [1 2]   '(3 2)))

(deftest moar-symbol-rewriting
  (are [form result] (= (>>> form) result)
       (map +:'3       [1 2 3])     '(4 5 6)
       (filter not=:'x ''[z y x w]) '(z y w)
       (map +:'3:'4    [2 3])       '(9 10)

       (map:.+:'3      [1 2 3])     '(4 5 6)

       (map:':foo ''[{:foo bar} {:foo bif}])  '(bar bif)
       (:foo..zipmap [:foo :bar] ''[bif baz]) 'bif
       (map:inc..:foo {:foo [1 3 5]})        '(2 4 6)))

(deftest functional-transform
  (are [form args result] (= ((>>> form) args) result)

       @[first last]       [1 2 3] [1 3]
       @[first [last]]     [1 2 3] [1 [3]]
       @#{first second}    [1 2 3] #{1 2}
       @#{first apply:max} [1 2 3] #{1 3}

       @{':first first}           [1 2 3] {:first 1}
       @{(+ first last) reduce:+} [1 2 3] {4 6}

       @(/ reduce:+ count) [1 3 5]           3
       @(zipmap keys vals) {':foo 1 ':bar 2} {:foo 1 :bar 2}
       @(zipmap vals keys) {':foo 1 ':bar 2} {1 :foo 2 :bar}

       @(identity identity)   5       5
       @(+ first last)        [1 2 3] 4
       @(+ (first rest) last) [1 2 3] 5

       @(if vector? first identity) [1 2 3] 1
       @(if vector? first identity) 3       3))

(deftest bindings
  (are [out in] (= in out)
       6  (>>> (sum [1 2 3])
               sum reduce:+)

       6  (>>> (f 5)
               (f x) (+ x 1))

       21 (>>> (f.g x)
               (f x) (+ x 1)
               (g x) (* x 2)
               x 10)

       5  (>>> (first.rest xs)
               xs          [1 ((add 2) 3)]
               ((add x) y) (+ x y))))

(>>>>
 (def (f x) (+ x 1))
 (def ((partial-fact base) n)
      (if (> n base)
        (* n (partial-fact'.dec n))
        base))

 (def separator " ")
 (def joiner s/join:separator)

 (deftest def-rewriting
   (are [in out] (= in out)
        (f 10)               11
        ((partial-fact 3) 5) 60

        (joiner [1 2 3]) "1 2 3")))