(ns spec-dict
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as sg]))


(def r-invalid (reduced ::s/invalid))


(defrecord DictSpec
    [key->spec
     req-keys
     gen-fn]

  s/Specize

  (specize* [s] s)
  (specize* [s _] s)

  s/Spec

  (conform* [_ key->value]
    (if (map? key->value)
      (reduce-kv
       (fn [key->value key spec]
         (if-let [entry (find key->value key)]
           (let [[_ value] entry
                 result (s/conform* spec value)]
             (if (s/invalid? result)
               r-invalid
               (assoc key->value key result)))
           (if (contains? req-keys key)
             r-invalid
             key->value)))
       key->value
       key->spec)
      ::s/invalid))

  (unform* [_ key->value]
    (reduce-kv
     (fn [key->value key spec]
       (if-let [entry (find key->value key)]
         (let [[_ value] entry
               result (s/unform* spec value)]
           (assoc key->value key result))
         key->value))
     key->value
     key->spec))

  (explain* [_ path via in key->value]

    (if-not (map? key->value)

      [{:path path :pred `map? :val key->value :via via :in in}]

      (reduce-kv
       (fn [problems key spec]

         (if-let [entry (find key->value key)]

           (let [[_ value] entry
                 result (s/conform* spec value)]
             (if (s/invalid? result)

               (conj problems {:val  value
                               :pred `dunno
                               :path (conj path key)
                               :via  (conj via spec)
                               :in   (conj in key)})

               problems))

           (if (contains? req-keys key)
             (conj problems {:val  nil
                             :pred `(contains? ~req-keys ~key)
                             :path (conj path key)
                             :via  (conj via spec)
                             :in   (conj in key)})

             problems)))
       []
       key->spec)))

  (gen* [_ overrides path rmap]
    (if gen-fn
      (gen-fn)
      (let [args* (transient [])]
        (doseq [[key spec] key->spec]
          (conj! args* key)
          (conj! args* (s/gen* spec overrides path rmap)))
        (apply sg/hash-map (persistent! args*)))))

  (with-gen* [this gfn]
    (assoc this :gen-fn gfn))

  (describe* [spec]
    (into {} (for [[key spec] key->spec]
               [key (s/describe* spec)]))))


(defn dict [key->spec & more]

  (let [key->spec* (transient {})
        req-keys* (transient #{})]

    (doseq [key->spec (cons key->spec more)]
      (doseq [[key spec] key->spec]
        (when-not (-> key->spec meta :opt)
          (conj! req-keys* key))
        (assoc! key->spec* key (s/specize* spec))))

    (->DictSpec (persistent! key->spec*)
                (persistent! req-keys*)
                nil)))




(s/def ::profile (dict {:site string?
                        :twitter string?}))

(s/def ::user (dict {:name string?
                     :age int?}))

(s/def ::account
  (dict {:user ::user
         :profile (dict {:site string?
                         :twitter string?})}))


(s/def ::account
  (dict {:user ::user
         :profile {:site string?
                   :twitter string?}}))


#_
(s/def ::account
  {:user {:site string?
          :twitter string?}
   :profile {:site string?
             :twitter string?}})


(s/def ::user (dict {"name" string?
                     "age" int?}))



#_
(s/def ::->int
  (s/conformer (fn [x]
                 (Integer/parseInt x))
               (fn [x]
                 (str x))))

#_
(s/conform (dict {:foo ::->int}) {:foo "42"})

#_
(s/conform (dict {:foo ::->int}) {:foo nil})

#_
(s/unform (dict {:foo ::->int}) {:foo 42 :test 444})

#_
(s/explain-data (dict {:foo ::->int}) {:foo nil})

;; #:clojure.spec.alpha{:problems
;;                      ({:path [],
;;                        :pred
;;                        (clojure.core/fn
;;                          [%]
;;                          (clojure.core/contains? % :aaa)),
;;                        :val #:foo{:aaa2 2},
;;                        :via [:spec-dict.core/foo],
;;                        :in []}),
;;                      :spec :spec-dict.core/foo,
;;                      :value #:foo{:aaa2 2}}

#_
(clojure.pprint/pprint
 (s/explain-data
  (dict {:aaa/test string?} ^:opt {:test/foo int?})
  {:aaa/test "sdfs" :test/foo "sdfsf"}))


#_
(clojure.pprint/pprint
 (s/describe
  (dict {:aaa/test string?} ^:opt {:test/foo int?})))
