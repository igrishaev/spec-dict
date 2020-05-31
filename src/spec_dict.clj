(ns spec-dict
  (:require
   [clojure.set :as set]
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as sg]))


(defn- error!
  [template & args]
  (throw (new Exception ^String (apply format template args))))


(def r-invalid (reduced ::s/invalid))


(defrecord DictSpec
    [key->spec
     req-keys
     gfn
     strict?]

    s/Specize

    (specize* [s] s)
    (specize* [s _] s)

    s/Spec

    (conform* [_ key->value]

      ;; check for map
      (if-not (map? key->value)
        ::s/invalid

        ;; check for strict keys (delay fetching keys)
        (if (and strict?
                 (not (set/subset?
                       (-> key->value keys set)
                       (-> key->spec keys set))))
          ::s/invalid

          ;; check key by key
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
           key->spec))))

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

      ;; map?
      (if-not (map? key->value)

        [{:reason "not a map"
          :path path
          :pred `map?
          :val key->value
          :via via
          :in in}]

        ;; strict keys
        (if (and strict?
                 (not (set/subset?
                       (-> key->value keys set)
                       (-> key->spec keys set))))

          [{:reason "extra keys"
            :path path
            :pred `(set/subset?
                    ~(-> key->value keys set)
                    ~(-> key->spec keys set))
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
           key->spec))))

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


(defn ->opt [mapping]
  (with-meta mapping {:opt true}))


(defn opt? [mapping]
  (some-> mapping meta :opt))


(defn- map->dict [source]
  (let [key->spec* (transient {})
        req-keys* (transient #{})]
    (doseq [[key spec] source]
      (when-not (opt? source)
        (conj! req-keys* key))
      (assoc! key->spec* key spec))
    (map->DictSpec
     {:key->spec (persistent! key->spec*)
      :req-keys (persistent! req-keys*)})))


(defn- spec-keys? [spec]
  (some-> spec s/form first (= `s/keys)))


(defn- unk [fqkw]
  (-> fqkw name keyword))


(defn- spec-or-any? [kwd]
  (if (s/get-spec kwd)
    kwd
    any?))


(declare dict) ;; ugly


(defn- keys->dict [spec-keys]

  (let [form (s/form spec-keys)

        {:keys [req opt req-un opt-un]}
        (apply hash-map (rest form))

        map-req
        (merge
         (into {} (for [spec req]
                    [spec (spec-or-any? spec)]))
         (into {} (for [spec req-un]
                    [(unk spec) (spec-or-any? spec)])))

        map-opt
        (merge
         (into {} (for [spec opt]
                    [spec (spec-or-any? spec)]))
         (into {} (for [spec opt-un]
                    [(unk spec) (spec-or-any? spec)]))

         ;; for compatibility with s/keys
         (into {} (for [spec opt-un]
                    [spec (spec-or-any? spec)]))
         (into {} (for [spec req-un]
                    [spec (spec-or-any? spec)])))]

    (dict map-req (->opt map-opt))))


(defn- keyword->dict [source]
  (if-let [spec (s/get-spec source)]
    (cond
      (dict? spec)
      spec

      (spec-keys? spec)
      (keys->dict spec)

      :else
      (error! "Not a dict spec: %s" source))

    (error! "Missing spec: %s" source)))


(defn- ->dict [source]

  (cond
    (keyword? source)
    (keyword->dict source)

    (dict? source)
    source

    (map? source)
    (map->dict source)

    :else
    (error! "Wrong dict param: %s" source)))


(defn- ->dict-merge [dict1 dict2]
  (let [{:keys [key->spec req-keys]} dict2]
    (-> dict1
        (update :key->spec into key->spec)
        (update :req-keys into req-keys))))


(defn dict [key->spec & more]
  (let [sources (map ->dict (cons key->spec more))]
    (reduce ->dict-merge sources)))


(defn strict [d]
  (assoc d :strict? true))


(defn dict* [key->spec & more]
  (let [d (apply dict key->spec more)]
    (strict d)))
