# Dictionary-like Specs

Maps are quite common in Clojure, and thus `s/keys` specs too. Here is a common
example:

```clojure
(s/def :profile/url    string?)
(s/def :profile/rating int?)
(s/def ::profile
  (s/keys :req-un [:profile/url
                   :profile/rating]))

(s/def :user/name    string?)
(s/def :user/age     int?)
(s/def :user/profile ::profile)
(s/def ::user
  (s/keys :req-un [:user/name
                   :user/age
                   :user/profile]))
```

What's wrong with it? Namely:

- each key requires its own spec, which is verbose;
- keys without a namespace still need it to declare a spec;
- for the top level map you use the current namespace, but for children you have
  to specify it manually, which leads to spec overriding (not only you have
  declared `:user/name`);
- keys are only keywords which is fine in 99%, but still;
- there is no a strict version of `s/keys` which fails when extra keys were
  passed. Doing it manually looks messy.

Now imagine if it would have been like this:

```clojure
(s/def ::user
  {:name string?
   :age int?
   :profile {:url? string?
             :rating int?}})
```

or this (full keys):

```clojure
(s/def ::user
  #:user{:name string?
         :age int?
         :profile #:profile{:url? string?
                            :rating int?}})
```

This library is it to fix everything said above. Add it:

```clojure
;; deps
[spec-dict "0.1.0"]

(require '[spec-dict :refer [dict dict*]])
```

A simple dictionary spec:

```clojure
(s/def ::user-simple
  (dict {:name string? :age int?}))

(s/valid? ::user-simple {:name "Ivan" :age 34})
```

By default, extra keys are OK:

```clojure
(s/valid? ::user-simple {:name "Ivan" :age 34 :extra 1})
```

Keys of different types:

```clojure
(s/def ::user-types
  (dict {"name" string?
         :age int?
         'active boolean?}))

(s/valid? ::user-types {"name" "Ivan" :age 34 'active true})
```

The dicts can be nested:

```clojure
(s/def ::post-nested
  (dict {:title string?
         :author (dict {:name string?
                        :email string?})}))

(s/valid? ::post-nested
          {:title "Hello"
           :author {:name "Ivan"
                    :email "test@test.com"}})
```

A dict may reference another dict:

```clojure
(s/def ::post-author
  (dict {:name string?
         :email string?}))


(s/def ::post-ref
  (dict {:title string?
         :author ::post-author}))
```

or be a part of a collection as well:

```clojure
(s/def ::post-coll-of
  (dict {:title string?
         :authors (s/coll-of ::post-author)}))
```

The inner map can be prefixed to get full keys:

```clojure
;; spec
(dict #:user{:extra/test boolean?
             :name string?
             :age int?})

;; data
{:extra/test false
 :user/name "Ivan"
 :user/age 34}
```

The dict consumes multiple maps on creation, the final keys get merged in the
same order:

```clojure
;; spec
(dict {:name string?} {:age int?})

;; data
{:name "Ivan" :age 34}
```

You can override types if you need:

```clojure
;; spec
(dict {:name string?}
      {:age int?}
      {:name int?})

;; data
{:name 42 :age 34}
```

By default, all the keys are required. To mark keys as optional, put the `^:opt`
metadata flag:

```clojure
;; spec
(dict {:name string?}
      ^:opt {:age int?})

;; data OK
{:name "Ivan" :age 34}
{:name "Ivan"}

;; data ERR
{:name "Ivan" :age nil}
```

A dict can reference any spec:

```clojure
(dict ::user-simple
      {:active :fields/boolean})
```

Conforming:

```clojure
(s/def ::->int
  (s/conformer (fn [x]
                 (try
                   (Integer/parseInt x)
                   (catch Exception e
                     ::s/invalid)))))

;; spec
(dict {:value ::->int})

(s/conform spec {:value "123"})
{:value 123}
```

Unforming:

```clojure
(s/def ::->int2
  (s/conformer (fn [x]
                 (try
                   (Integer/parseInt x)
                   (catch Exception e
                     ::s/invalid)))
               (fn [x]
                 (str x))))

;; spec
(dict {:value ::->int2})

(s/unform spec (s/conform spec {:value "123"}))
{:value "123"}
```

Strict version of a dict which fails when extra keys were passed:


```clojure
;; spec
(dict* {:name string?
        :age int?}
       ^:opt {:active boolean?})

;; data OK
{:name "test" :age 34}
{:name "test" :age 34 :active true}

;; data ERR
{:name "test" :age 34 :extra "aa"}
{:name "test" :age 34 :active true :extra "aa"}
```

Generators:

```clojure

;; spec
(dict {:name #{"Ivan" "Juan" "Iogann"}
       :age int?})


(gen/generate (s/gen spec))
{:name "Iogann" :age -2}
```

Explain:

```clojure
;; spec
(dict {:name ::some-name
       :age int?})

;; not a map
(s/explain-data spec 123)

;; problem
{:reason "not a map"
 :path []
 :pred clojure.core/map?
 :val 123
 :via []
 :in []}

;; missing key
(s/explain-data spec {:age 34})

;; problem
{:reason "missing key"
 :val nil
 :pred (clojure.core/contains? #{:age :name} :name)
 :path [:name]
 :via [:spec-dict-test/some-name]
 :in [:name]}

;; wrong value
(s/explain-data spec {:name 123 :age 43})

;; problem
{:reason "spec failure"
 :val 123
 :pred clojure.core/string?
 :path [:name]
 :via [:spec-dict-test/some-name]
 :in [:name]}
```

Explain for a strict version:

```clojure
;; spec
(dict* {:name string?
        :age int?})

;; extra key in a strict dict
(s/explain-data spec {:name "Ivan" :age 34 :extra true})

;; problem

{:reason "extra keys"
 :path []
 :pred (clojure.set/subset? #{:age :name :extra} #{:age :name})
 :val {:name "Ivan" :age 34 :extra true}
 :via []
 :in []}
```

A dictionary spec supports `s/keys`. A `s/keys` one gets converted into a
dictionary keeping in mind all type of keys: `req`, `req-opt`, `opt`, and
`opt-un`:

```clojure
(s/def :profile/url    string?)
(s/def :profile/rating int?)
(s/def ::profile
  (s/keys :req-un [:profile/url
                   :profile/rating]))

(s/def :user/name    string?)
(s/def :user/age     int?)
(s/def :user/profile ::profile)
(s/def ::user
  (s/keys :req-un [:user/name
                   :user/age
                   :user/profile]))


;; profile spec
(dict ::profile)

;; data
{:url "http://test.com"
 :rating 99.99}
```

Having a dict spec makes it easier to merge other keys:

```clojure
(let [spec-p (dict ::profile {:paid boolean?})
      spec-u (dict ::user {:profile spec-p
                           :active? boolean?})]
  ...)


;; data for spec-u
{:name "test"
 :age 42
 :active? true
 :profile {:url "http://test.com"
           :rating 99
           :paid true}}
```
