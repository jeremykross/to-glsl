(ns to-glsl.core
  (:require
    fipp.visit
    clojure.walk
    [camel-snake-kebab.core :as csk]
    [clojure.string :as string]
    [fipp.engine :as fipp]))

(defn- compile-symbol
  [_ x]
  (let [x-str (str x)]
    [:text (if (string/starts-with? x-str "gl/")
             (str "gl_" (csk/->PascalCase (string/replace x-str "gl/" "")))
             (str (csk/->camelCase x-str)))]))

(defn- compile-typed
  [visit definition]
  (let [arg (last definition)
        arg-type (drop-last definition)]
    [:span [:text (string/join " " (map #(csk/->camelCase (str %)) arg-type))] " " (compile-symbol visit arg)]))

(defn- compile-def
  [visit [_ t n initial-value]]
  [:span (compile-typed visit (flatten [t n])) (if initial-value [:span [:text " = "] (visit initial-value)]) ])

(defn- compile-def-varying
  [visit x]
  (into [:span "varying "] (compile-def visit x)))

(defn- compile-def-uniform
  [visit x]
  (into [:span "uniform "] (compile-def visit x)))

(defn- compile-def-attribute
  [visit x]
  (into [:span "attribute "] (compile-def visit x)))

(defn- compile-do
  [visit [_ & forms]]
  [:group
   (map (fn [x] [:group
                 (visit x)
                 (if (not (contains? #{'if 'when 'defn} (first x)))
                   ";")
                 :break]) forms)])

(defn- compile-defn
  [visit [_ n  [return-type args] & body]]
  [:span
   [:text return-type " " (csk/->camelCase n)]
   [:group "(" (interpose ", " (map (partial compile-typed visit) args)) ") "]
   [:group "{" :break [:nest 2 (visit `(do ~@body))] "}"]])

(defn- compile-when
  [visit [_ condition & body]]
  [:span "if (" (visit condition) ") " [:group "{" :break [:nest 2 (visit `(do ~@body))] "}"]])

(defn- compile-if
  [visit [_ condition success fail]]
  [:span (visit condition) " ? " (visit success) " : " (visit fail)])

(defn- compile-set!
  [visit [_ dest source]]
  [:span (compile-symbol visit dest) " = " (visit source) ])

(defn- compile-inc
  [visit [_ n]]
  [:span (compile-symbol visit n) "++"])

(defn- compile-dec
  [visit [_ n]]
  [:span (compile-symbol visit n) "--"])

(defn- compile-field
  [visit [_ n access]]
  [:span (compile-symbol visit n) "." (str access)])

(defn- compile-swizzle
  [visit [_ n & elements]]
  [:span (compile-symbol visit n) "." (string/join (map str elements))])

(defn- compile-default
  [visit [n & args]]
  [:group (compile-symbol visit n) "(" (interpose ", " (map visit args)) ")"])

(defn- infix
  [op]
  (fn [visit [_ & args]]
    [:span (interpose (str " " op " ") (map visit args))]))

(defn- compile-form
  [printer x]
  (let [compile-fn
        (condp = (first x)
          'defn compile-defn
          'def compile-def
          'def-varying compile-def-varying
          'def-uniform compile-def-uniform
          'def-attribute compile-def-attribute
          'when compile-when
          'if compile-if
          'and (infix "&&")
          'or (infix "||")
          '= (infix "==")
          '+ (infix "+")
          '- (infix "-")
          '/ (infix "/")
          '* (infix "*")
          'set! compile-set!
          'bit-and (infix "&")
          'bit-or (infix "|")
          'bit-xor (infix "^")
          'bit-not (infix "~")
          'bit-shift-right (infix ">>")
          'bit-shift-left (infix "<<")
          'inc compile-inc
          'dec compile-dec
          'field compile-field
          'swizzle compile-swizzle
          'do compile-do
          compile-default)]
    (compile-fn (partial fipp.visit/visit printer) x)))

(defrecord GLSLPrinter []
  fipp.visit/IVisitor
  (visit-meta [this meta x]
    (fipp.visit/visit* this x))
  (visit-string [this x]
    [:text "\"" x "\""])
  (visit-number [this x]
    [:text (str x)])
  (visit-boolean [this x]
    [:text (str x)])
  (visit-seq [this x]
    (compile-form this x))
  (visit-symbol [this x]
    (compile-symbol this x)))

(defn- pretty
  [x]
  (fipp.visit/visit (GLSLPrinter.) x))

(defn locations
  [form]
  (let [locs (atom {:attributes []
                    :uniforms []})
        add! (fn [k [_ t loc]] (swap! locs update k conj 
                                      {:type (str t)
                                       :location (str loc)
                                       :gl-location (csk/->camelCase (str loc))}))]
    (clojure.walk/prewalk
      (fn [x] 
        (when (and (list? x) (contains? #{'def-attribute 'def-varying 'def-uniform} (first x)))
          (condp = (first x)
            'def-attribute (add! :attribute x)
            'def-uniform (add! :uniform x)))
        x)
      form)
    @locs))

(defn ->glsl
  "Converts form to GLSL"
  [form]
  (let [pretty-data (pretty form)]
    (with-out-str
      (fipp/pprint-document pretty-data {:width 40}))))
