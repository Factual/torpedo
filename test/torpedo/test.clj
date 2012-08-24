(ns torpedo.test
  (:use [torpedo] :reload)
  (:use [clojure.test]))

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

(deftest functional-transform
  (are [form args result] (= ((>>> form) args) result)

       @[first last]       [1 2 3] [1 3]
       @[first [last]]     [1 2 3] [1 [3]]
       @#{first second}    [1 2 3] #{1 2}
       @#{first apply:max} [1 2 3] #{1 3}

       @(/ reduce:+ count)    [1 3 5]         3
       @(zipmap keys vals)    {:foo 1 :bar 2} {:foo 1 :bar 2}
       @(zipmap vals keys)    {:foo 1 :bar 2} {1 :foo 2 :bar}
       @(identity identity)   5               5
       @(+ first last)        [1 2 3]         4
       @(+ (first rest) last) [1 2 3]         5))

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
