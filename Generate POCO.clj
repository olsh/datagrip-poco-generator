(def usings "using System;")
(def default-type "string")
(def type-mappings
    [
        [["bit"] "bool"]
        [["tinyint"] "byte"]
        [["uniqueidentifier"] "Guid"]
        [["int"] "int"]
        [["bigint"] "long"]
        [["char"] "char"]
        [["varbinary" "image"] "byte[]" true] ; cannot be null
        [["double" "float" "real"] "double"]
        [["decimal" "money" "numeric" "smallmoney"] "decimal"]
        [["datetime" "timestamp" "date" "time"] "DateTime"]
        [["datetimeoffset"] "DateTimeOffset"]
    ])
(def new-line "\r\n")

(defn- poco-name [name]
    (apply str (map clojure.string/capitalize (re-seq #"(?:[A-Z]+)?[a-z\d]*" name))))

(defn- poco-type [data-type is-null]
    (let [spec                (.. data-type getSpecification toLowerCase)
          spec-matches?       (fn [pattern] (= (re-find #"^\w+" spec) pattern))
          mapping-matches?    (fn [[ps t n]] (when (some spec-matches? ps) [t n]))
          [type cant-be-null]  (some mapping-matches? type-mappings)
          nullable-type       (if (and type (not cant-be-null) is-null) (str type "?") type)]
        (or nullable-type default-type)))

(defn- field-infos [table]
    (let [columns    (com.intellij.database.util.DasUtil/getColumns table)
          field-info (fn [column] {:name (poco-name (.getName column))
                                   :type (poco-type (.getDataType column) (not (.isNotNull column)))})]
        (map field-info columns)))

(defn- property-text [field-info]
    (let [type  (:type field-info)
          name  (:name field-info)]
        (str "  public " type " " name " { get; set; } " new-line)))

(defn- poco-text [class-name fields]
    (apply str (flatten
        [usings new-line new-line
         "public class " class-name " " new-line "{" new-line
         (interpose new-line (interleave (map property-text fields)))
         "}" new-line])))

(defn- generate-poco [directory table]
    (let [class-name (poco-name (.getName table))
          fields     (field-infos table)
          file       (java.io.File. directory (str class-name ".cs"))
          text       (poco-text class-name fields)]
        (com.intellij.openapi.util.io.FileUtil/writeToFile file text)))

(defn- generate-pocos [directory]
    (let [table? (partial instance? com.intellij.database.model.DasTable)]
        (doseq [table (filter table? SELECTION)]
            (generate-poco directory table))))


(.chooseDirectoryAndSave FILES
                         "Choose directory"
                         "Choose where to generate POCOs to"
                         (proxy [com.intellij.util.Consumer] []
                            (consume [directory]
                                (generate-pocos directory)
                                (.refresh FILES directory))))
