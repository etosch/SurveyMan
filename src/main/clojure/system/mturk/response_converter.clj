(ns system.mturk.response-converter
  (:require [clojure.java.io :as io])
  (:require [clojure.data.json :as json])
  (:require [clojure.data.csv :as csv])
  (:import (java.io FileWriter File FileReader Writer)
           (input.csv CSVLexer CSVParser)
           (survey Survey Question Component StringComponent)))

(def mturk-headers '(HitId HitTitle Annotation AssignmentId WorkerId Status AcceptTime SubmitTime))
(def output-headers '(responseid workerid surveyid questionid questiontext questionpos optionid optiontext optionpos))
(def workerid-index 4)
(def srid (atom 0))
;; utility to take the csv output of mturk and convert it to our csv output

(defn print-headers
  [^FileWriter w filename ^Survey s]
  (when-not (and (.exists (File. filename))
                 (not (empty? (line-seq (io/reader filename)))))
    (.write w (str (clojure.string/join "," (concat output-headers (.otherHeaders s))) "\n")))
  )

(defn custom?
  [^Question q]
  (= "q_-1_-1" (.quid q))
  )

(defn other-headers
  [^Survey s ^Question q]
  (if (custom? q)
    (for [header (.otherHeaders s)]
      "")
    (for [header (.otherHeaders s)]
      (.get (.otherValues q) header))
    )
  )

(defn old-format
  [filename]
  ;; such hackage
  (let [contents (slurp filename)]
    (and (.contains contents ";") (.contains contents ",comp")))
  )

(defn get-question-by-oid
  [^Survey s optionid]
  (loop [qlist (.questions s)]
    (if-let [retval (when-not (empty? qlist)
                      (try
                        (.getOptById (first qlist) optionid)
                        (first qlist)
                        (catch Exception e nil)))]
      retval
      (recur (rest qlist))
      )
    )
  )

(defn write-line
  [^Writer w ^Survey s ^Question q ^Component o srid workerid questionpos optionpos]
  (csv/write-csv w [(concat (list srid workerid "survey1" (.quid q) (.data q) questionpos (.getCid o) o optionpos)
                            (other-headers s q))])
  )

(defn write-aux-resp
  [^Writer w ^Survey s srid line resp]
  (let [^Question q (Question. resp -1 -1)
        ^Component o (StringComponent. resp -1 -1)]
    (write-line w s q o srid (nth line workerid-index) -1 -1))
  )

(defn parse-old-format
  [filename ^Survey s ^FileWriter w]
  (with-open [r (io/reader filename)]
    (doseq [line (rest (csv/read-csv r))]
      (let [answers (drop (count mturk-headers) line)
            srid (str "sr" (swap! srid inc))]
        (doseq [ans answers]
          (doseq [resp (clojure.string/split ans #"\|")]
            (if (re-matches #"comp_[0-9]+_[0-9]+;[0-9]+;[0-9]+" resp)
              (let [[optionid questionpos optionpos] (clojure.string/split resp #";")
                    ^Question q (get-question-by-oid s optionid)
                    ^Component o (.getOptById q optionid)]
                (write-line w s q o srid (nth line workerid-index) questionpos optionpos))
               (write-aux-resp w s srid line resp))
            )
          )
        )
      )
    )
  )

(defn parse-new-format
  [filename ^Survey s ^FileWriter w]
  (with-open [r (io/reader filename)]
    (doseq [line (rest (csv/read-csv r))]
      (let [answers (->> line
                         (drop (count mturk-headers))
                         (remove empty?)
                         (remove #(and (string? %) (.endsWith % ".csv"))))
            srid (str "sr" (swap! srid inc))]
        (doseq [ans answers]
          ;(println ans)
          (doseq [resp (clojure.string/split ans #"\|")]
            (let [thing (json/read-str resp)]
              (if (map? thing)
                (let [{quid "quid" qpos "qpos" oid "oid" opos "opos"} thing
                      ^Question q (.getQuestionById s quid)
                      ^Component o (.getOptById q oid)]
                  (write-line w s q o srid (nth line workerid-index) qpos opos))
                (write-aux-resp w s srid line resp)
                )
              )
            )
          )
        )
      )
    )
  )

(defn -main
  [& args]
  (let [filename (first args)
        ^Survey s (-> (second args)
                   (CSVLexer. ",")
                   (CSVParser.)
                   (.parse))
        output-filename "results.csv"]
    (reset! srid (if (>= (count args) 3) (read-string (nth args 2)) 0))
    (with-open [w (io/writer output-filename :append true)]
      (print-headers w output-filename s)
      (if (old-format filename)
        (parse-old-format filename s w)
        (parse-new-format filename s w))
      )
    )
  (print @srid)
  )