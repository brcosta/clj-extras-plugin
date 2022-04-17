(ns kondoutil)
(defn run-with-in-str [code cfg] (with-in-str code (clj-kondo.core/run! cfg)))
