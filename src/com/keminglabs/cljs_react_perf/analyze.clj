(ns com.keminglabs.cljs-react-perf.analyze
  (:require [clojure.edn :as edn]
            [rum.core :as rum]))

;;Some stats from
;;https://github.com/clojure-cookbook/clojure-cookbook/blob/master/01_primitive-data/1-20_simple-statistics.asciidoc

(defn mean
  [coll]
  (let [sum (apply + coll)
        count (count coll)]
    (if (pos? count)
      (double (/ sum count))
      0.0)))


(defn standard-deviation
  [coll]
  (let [avg (mean coll)
        squares (for [x coll]
                  (let [x-avg (- x avg)]
                    (* x-avg x-avg)))
        total (count coll)]
    (-> (/ (apply + squares)
           (- total 1))
        (Math/sqrt))))


(def css
  "

  td.number {
  text-align: right;
  font-family: monospace;
  }


"
  )



(comment

  (def results
    (->> (str "[" (slurp "foo.edn") "]")
         edn/read-string))


  (spit "foo.html"
    (rum/render-static-markup
     [:html
      [:head
       [:meta {:charset "utf-8"}]
       [:style css]]

      [:body
       [:table
        [:thead
         [:th "Name"]
         [:th "Time (ms)"]
         [:th "Heap (bytes)"]]

        [:tbody
         (for [[name runs] (group-by :name results)
               run (->> runs
                        (sort-by :optimization-mode))]

           (let [;;Ignore the initial render time --- only want to measure re-render time
                 measurements (drop 1 (:measurements run))

                 ts (->> measurements
                         (mapcat :tufte)
                         (map :duration))

                 mems (map :private-memory measurements)]

             [:tr
              [:td (:name run) " "
               "(" (:optimization-mode run) ")"]

              [:td.number (str (Math/round (mean ts))
                               " ± "
                               (Math/round (standard-deviation ts)))]

              [:td.number (str (Math/round (mean mems))
                               " ± "
                               (Math/round (standard-deviation mems)))]]))]]]])))
