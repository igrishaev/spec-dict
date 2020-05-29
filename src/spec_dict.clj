(ns spec-dict
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as sg]))


(defn error!
  [template & args]
  (throw (new Exception ^String (apply format template args))))


(def r-invalid (reduced ::s/invalid))


(defrecord DictSpec
    [key->spec
     req-keys
     gfn]

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

        [{:reason "not a map"
          :path path
          :pred `map?
          :val key->value
          :via via
          :in in}]

        (reduce-kv
         (fn [problems key spec]

           (if-let [entry (find key->value key)]

             (let [[_ value] entry
                   result (s/conform spec value)]
               (if (s/invalid? result)

                 (conj problems {:reason "spec failure"
                                 :val  value
                                 :pred (s/form spec)
                                 :path (conj path key)
                                 :via  (conj via spec)
                                 :in   (conj in key)})

                 problems))

             (if (contains? req-keys key)
               (conj problems {:reason "missing key"
                               :val  nil
                               :pred `(contains? ~req-keys ~key)
                               :path (conj path key)
                               :via  (conj via spec)
                               :in   (conj in key)})

               problems)))
         []
         key->spec)))

    (gen* [_ overrides path rmap]
      (if gfn
        (gfn)
        (let [args* (transient [])]
          (doseq [[key spec] key->spec]
            (conj! args* key)
            (conj! args* (s/gen spec overrides)))
          (apply sg/hash-map (persistent! args*)))))

    (with-gen* [this gfn]
      (assoc this :gfn gfn))

    (describe* [spec]
      (into {} (for [[key spec] key->spec]
                 [key (s/describe spec)]))))


(def dict? (partial instance? DictSpec))


(defn ->dict [source]

  (cond
    (keyword? source)
    (if-let [spec (s/get-spec source)]
      (if (dict? spec)
        spec
        (error! "Not a dict spec: %s" source))
      (error! "Missing spec: %s" source))

    (dict? source)
    source

    (map? source)
    (let [key->spec* (transient {})
          req-keys* (transient #{})]
      (doseq [[key spec] source]
        (when-not (-> source meta :opt)
          (conj! req-keys* key))
        (assoc! key->spec* key spec))
      (map->DictSpec
       {:key->spec (persistent! key->spec*)
        :req-keys (persistent! req-keys*)}))

    :else
    (error! "Wrong dict param: %s" source)))


(defn ->dict-merge [dict1 dict2]
  (let [{:keys [key->spec req-keys]} dict2]
    (-> dict1
        (update :key->spec into key->spec)
        (update :req-keys into req-keys))))


(defn dict [key->spec & more]
  (let [sources (map ->dict (cons key->spec more))]
    (reduce ->dict-merge sources)))
