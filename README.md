# Dictionary-like Specs

```clojure

(s/def ::user-simple
  (dict {:name string? :age int?}))


(s/def ::user-types
  (dict {"name" string?
         :age int?
         'active boolean?}))


(s/def ::post-nested
  (dict {:title string?
         :author {:name string?
                  :email string?}}))

(s/def ::post-ref
  (dict {:title string?
         :author ::post-author}))


(s/def ::post-coll-of
  (dict {:title string?
         :authors (s/coll-of ::post-author)}))


(dict #:user{:name string?
             :age int?})

(dict #:user{:extra/test boolean?
             :name string?
             :age int?})

(dict {:foo {:bar {:baz {:test {:dunno {:what string?}}}}}})


(dict {:name string?}
      ^:opt {:age int?})

(dict ::user-simple
      {:active boolean?})
```
