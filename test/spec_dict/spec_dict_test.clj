(ns spec-dict-test
  (:require
   [spec-dict :refer [dict]]
   [clojure.spec.alpha :as s]
   [clojure.test :refer :all]))


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


(deftest test-valid-key-nested

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
