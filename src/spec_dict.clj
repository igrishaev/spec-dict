(ns spec-dict
  (:require
   [clojure.spec.alpha :as s]
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
                   result (s/conform spec value)]
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
                 result (s/unform spec value)]
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
                   result (s/conform spec value)]
               (if (s/invalid? result)

                 (conj problems {:val  value
                                 :pred (s/form spec)
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
            (conj! args* (s/gen spec overrides))
            #_
            (conj! args* (s/gen* spec overrides path rmap)))
          (apply sg/hash-map (persistent! args*)))))

    (with-gen* [this gfn]
      (assoc this :gen-fn gfn))

    (describe* [spec]
      (into {} (for [[key spec] key->spec]
                 [key (s/describe spec)]))))


(def dict? (partial instance? DictSpec))


(defn error!
  [template & args]
  (throw (new Exception ^String (apply format template args))))


(defn dict [key->spec & more]

  (let [key->spec* (transient {})
        req-keys* (transient #{})]

    (doseq [key->spec (cons key->spec more)]

      (cond
        (keyword? key->spec)
        (if-let [spec (s/get-spec key->spec)]
          (if (dict? spec)
            (let [{:keys [key->spec
                          req-keys]} spec]
              (doseq [[key spec] key->spec]
                (assoc! key->spec* key spec))
              (doseq [key req-keys]
                (conj! req-keys* key)))
            (error! "Not a dict spec: %s" key->spec))
          (error! "Missing spec: %s" key->spec))

        (dict? key->spec)
        (let [{:keys [key->spec
                      req-keys]} key->spec]
          (doseq [[key spec] key->spec]
            (assoc! key->spec* key spec))
          (doseq [key req-keys]
            (conj! req-keys* key)))

        (map? key->spec)
        (doseq [[key spec] key->spec]
          (when-not (-> key->spec meta :opt)
            (conj! req-keys* key))
          (assoc! key->spec* key spec))

        :else
        (error! "Wrong dict param: %s" key->spec)))

    (->DictSpec (persistent! key->spec*)
                (persistent! req-keys*)
                nil)))
