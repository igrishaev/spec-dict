(ns spec-dict-test
  (:require
   [spec-dict :refer [dict dict*]]
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as sg]
   [clojure.test.check.generators :as gen]
   [clojure.test :refer :all]))


(s/def ::user-simple
  (dict {:name string? :age int?}))


(s/def ::user-types
  (dict {"name" string?
         :age int?
         'active boolean?}))


(s/def ::post-nested
  (dict {:title string?
         :author (dict {:name string?
                        :email string?})}))


(s/def ::post-author
  (dict {:name string?
         :email string?}))


(s/def ::post-ref
  (dict {:title string?
         :author ::post-author}))


(s/def ::post-coll-of
  (dict {:title string?
         :authors (s/coll-of ::post-author)}))


(deftest test-valid-simple

  (doseq [sample [{:name "Ivan" :age 34}
                  {:name "" :age -1}
                  {:name "\t" :age 0 :extra nil :foo 42}]]

    (is (s/valid? ::user-simple sample)))

  (doseq [sample [{:name nil :age 34}
                  {:age -1}
                  {:Name "test" :age 32}
                  {:foo/name "test" :bar/age 32}]]

    (is (not (s/valid? ::user-simple sample)))))


(deftest test-valid-key-types

  (doseq [sample [{"name" "Ivan" :age 34 'active true}]]

    (is (s/valid? ::user-types sample)))

  (doseq [sample [{"name" "Ivan" :age 34 :active true}]]

    (is (not (s/valid? ::user-types sample)))))


(deftest test-valid-key-nested

  (doseq [sample [{:title "Hello"
                   :author {:name "Ivan" :email "test@test.com"}}]]

    (is (s/valid? ::post-nested sample)))

  (doseq [sample [{:title "Hello"
                   :author {:test/name "Ivan" :Email "test@test.com"}}

                  {:title "Hello"
                   :author {:name nil :email "test@test.com"}}

                  {:title "Hello"
                   :author {:name "Ivan" :email 42}}

                  {:title "Hello"
                   :author nil}]]

    (is (not (s/valid? ::post-nested sample)))))


(deftest test-valid-key-nested-ref

  (doseq [sample [{:title "Hello"
                   :author {:name "Ivan" :email "test@test.com"}}]]

    (is (s/valid? ::post-ref sample)))

  (doseq [sample [{:title "Hello"
                   :author {:test/name "Ivan" :Email "test@test.com"}}

                  {:title "Hello"
                   :author {:name nil :email "test@test.com"}}

                  {:title "Hello"
                   :author {:name "Ivan" :email 42}}

                  {:title "Hello"
                   :author nil}]]

    (is (not (s/valid? ::post-ref sample)))))


(deftest test-valid-coll-of

  (doseq [sample [{:title "Hello"
                   :authors [{:name "Ivan" :email "test@test.com"}]}]]

    (is (s/valid? ::post-coll-of sample)))


  (doseq [sample [{:title "Hello"
                   :authors [{:name "Ivan" :email "test@test.com"}
                             nil
                             {:name "Ivan" :email "test@test.com"}]}]]

    (is (not (s/valid? ::post-coll-of sample)))))


(deftest test-valid-coll-of-2

  (let [spec
        (dict {:title string?
               :authors (s/coll-of
                         (dict {:name string?
                                :email string?}))})]


    (doseq [sample [{:title "Hello"
                     :authors [{:name "Ivan" :email "test@test.com"}]}]]

      (is (s/valid? spec sample)))


    (doseq [sample [{:title "Hello"
                     :authors [{:name "Ivan" :email "test@test.com"}
                               nil
                               {:name "Ivan" :email "test@test.com"}]}]]

      (is (not (s/valid? spec sample))))))


(deftest test-valid-full-keys

  (let [spec
        (dict #:user{:name string?
                     :age int?})]


    (doseq [sample [{:user/name "Ivan"
                     :user/age 34}]]

      (is (s/valid? spec sample)))

    (doseq [sample [{:name "Ivan"
                     :user/age 34}]]

      (is (not (s/valid? spec sample))))))


(deftest test-valid-full-keys-mixed

  (let [spec
        (dict #:user{:extra/test boolean?
                     :name string?
                     :age int?})]


    (doseq [sample [{:extra/test false
                     :user/name "Ivan"
                     :user/age 34}]]

      (is (s/valid? spec sample)))

    (doseq [sample [{:extra/test false
                     :name "Ivan"
                     :user/age 34}]]

      (is (not (s/valid? spec sample))))))


(deftest test-valid-quite-nested

  (let [spec
        (dict {:foo (dict {:bar (dict {:baz string?})})})]

    (doseq [sample [{:foo {:bar {:baz "test"}}}]]

      (is (s/valid? spec sample)))

    (doseq [sample [{:foo {:bar {:baz 1}}}]]

      (is (not (s/valid? spec sample))))))


(deftest test-valid-multiple-maps

  (let [spec
        (dict {:name string?}
              {:age int?})]

    (doseq [sample [{:name "Ivan" :age 34}]]

      (is (s/valid? spec sample)))

    (doseq [sample [{:name "Ivan"}
                    {:age 34}]]

      (is (not (s/valid? spec sample))))))


(defn test-ok [spec samples]
  (doseq [sample samples]
    (is (s/valid? spec sample))))


(defn test-err [spec samples]
  (doseq [sample samples]
    (is (not (s/valid? spec sample)))))


(deftest test-valid-multiple-maps-override
  (let [spec (dict {:name string?}
                   {:age int?}
                   {:name int?})]

    (test-ok spec [{:name 42 :age 34}])

    (test-err spec [{:name "Ivan" :age 34}])))


(deftest test-valid-multiple-maps-opt
  (let [spec (dict {:name string?}
                   ^:opt {:age int?})]

    (test-ok spec [{:name "Ivan" :age 34}
                   {:name "Ivan" :test/age 42}
                   {:name "Ivan"}])

    (test-err spec [{:name "Ivan" :age nil}])))


(deftest test-valid-dict-reference
  (let [spec (dict ::user-simple
                   {:active boolean?})]

    (test-ok spec [{:name "Ivan" :age 34 :active true}])

    (test-err spec [{:name "Ivan" :age 34}])))


(deftest test-valid-multiple-maps-merge
  (let [fields1 (dict {:name string?})
        fields2 (dict {:age int?})
        fields3 (dict {:active boolean?})

        spec (dict fields1 fields2 fields3)]

    (test-ok spec [{:name "Ivan" :age 34 :active true}])

    (test-err spec [{:name "Ivan"}])))


(s/def ::->int
  (s/conformer (fn [x]
                 (try
                   (Integer/parseInt x)
                   (catch Exception e
                     ::s/invalid)))))


(deftest test-conform
  (let [spec (dict {:value ::->int})]

    (let [result (s/conform spec {:value "123"})]
      (is (= {:value 123} result)))

    (let [result (s/conform spec {:value "test"})]

      (is (s/invalid? result)))))


(deftest test-conform-nested
  (let [spec (dict {:foo (dict {:bar (dict {:baz ::->int})})})]

    (let [result (s/conform spec {:foo {:bar {:baz "123"}}})]
      (is (= {:foo {:bar {:baz 123}}} result)))))


(deftest test-conform-optional
  (let [spec (dict {:foo (dict {:bar (dict ^:opt {:baz ::->int})})})]

    (let [result (s/conform spec {:foo {:bar {:baz "123"}}})]
      (is (= {:foo {:bar {:baz 123}}} result)))

    (let [result (s/conform spec {:foo {:bar {:aaa "123"}}})]
      (is (= {:foo {:bar {:aaa "123"}}} result)))))


(deftest test-generator-ok
  (let [names #{"Ivan" "Juan" "Iogann"}
        spec (dict {:name names
                    :age int?})
        gen (s/gen spec)]

    (dotimes [_ 10]
      (let [sample (gen/generate gen)]
        (is (contains? names (:name sample)))
        (is (int? (:age sample)))
        (is (= #{:name :age} (set (keys sample))))))))


(deftest test-with-generator
  (let [names #{"Ivan" "Juan" "Iogann"}

        spec (dict {:name string?}
                   ^:opt {:age int?})

        gfn (fn []
              (sg/hash-map :name (s/gen names)))

        spec* (s/with-gen spec gfn)

        gen (s/gen spec*)]

    (dotimes [_ 10]
      (let [sample (gen/generate gen)]
        (is (contains? names (:name sample)))
        (is (= #{:name} (set (keys sample))))))))


(s/def ::some-name string?)


(deftest test-explain-wrong-spec
  (let [spec (dict {:name ::some-name
                    :age int?})]

    (let [explain (s/explain-data spec {:name 123 :age 43})
          {::s/keys [problems]} explain
          [problem] problems]

      (is (= '{:reason "spec failure"
               :val 123
               :pred clojure.core/string?
               :path [:name]
               :via [:spec-dict-test/some-name]
               :in [:name]}
             problem)))))


(deftest test-explain-not-map
  (let [spec (dict {:name ::some-name
                    :age int?})]

    (let [explain (s/explain-data spec 123)
          {::s/keys [problems]} explain
          [problem] problems]

      (is (= '{:reason "not a map"
               :path []
               :pred clojure.core/map?
               :val 123
               :via []
               :in []}
             problem)))))


(deftest test-explain-missing-key
  (let [spec (dict {:name ::some-name
                    :age int?})]

    (let [explain (s/explain-data spec {:age 34})
          {::s/keys [problems]} explain
          [problem] problems]

      (is (= '{:reason "missing key"
               :val nil
               :pred (clojure.core/contains? #{:age :name} :name)
               :path [:name]
               :via [:spec-dict-test/some-name]
               :in [:name]}
             problem)))))


(deftest test-explain-strict-keys
  (let [spec (dict* {:name string?
                     :age int?})]

    (let [explain (s/explain-data spec {:name "Ivan" :age 34 :extra true})
          {::s/keys [problems]} explain
          [problem] problems]

      (is (= '{:reason "extra keys"
               :path []
               :pred (clojure.set/subset? #{:age :name :extra} #{:age :name})
               :val {:name "Ivan" :age 34 :extra true}
               :via []
               :in []}
             problem)))))


(deftest test-describe-spec-simple
  (let [spec (dict {:name ::some-name
                    :age int?})]

    (let [result (s/describe spec)]

      (is (= '{:name string? :age int?}
             result)))))


(deftest test-describe-spec-nested
  (let [spec (dict {:name string?
                    :age int?
                    :profile (dict {:url string?
                                    :rating int?})})]

    (let [result (s/describe spec)]

      (is (= '{:name string? :age int? :profile {:url string? :rating int?}}
             result)))))


(s/def ::->int2
  (s/conformer (fn [x]
                 (try
                   (Integer/parseInt x)
                   (catch Exception e
                     ::s/invalid)))
               (fn [x]
                 (str x))))


(deftest test-unform-simple

  (let [spec (dict {:value ::->int2})]

    (let [result1 (s/conform spec {:value "123"})
          result2 (s/unform  spec result1)]

      (is (= {:value 123} result1))
      (is (= {:value "123"} result2)))))


(deftest test-strict-keys-simple

  (let [spec (dict* {:name string?
                     :age int?})]

    (test-ok spec [{:name "test" :age 34}])

    (test-err spec [{:name "test" :age 34 :extra "aa"}
                    {:name "test" :age 34 :foo "AAA"}
                    {:name "test" :age 34 :a nil}
                    {:name "test" :age 34 nil "AAA"}])))


(deftest test-strict-keys-optional

  (let [spec (dict* {:name string?
                     :age int?}
                    ^:opt {:active boolean?})]

    (test-ok spec [{:name "test" :age 34}
                   {:name "test" :age 34 :active true}])

    (test-err spec [{:name "test" :age 34 :extra "aa"}
                    {:name "test" :age 34 :active true :extra "aa"}])))
