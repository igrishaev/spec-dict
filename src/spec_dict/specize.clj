(ns spec-dict.specize
  (:require
   [spec-dict :as sd]
   [clojure.spec.alpha :as s]))


(extend-protocol s/Specize

  clojure.lang.IPersistentMap
  (specize*
    ([k]
     (println k)
     (sd/dict k))
    ([k _]
     (println k)
     (sd/dict k))))
