(ns bestinclass.core
  (:use     clojure.contrib.io
            bestinclass.admin)
  (:require ring.util.servlet)
  (:gen-class :extends javax.servlet.http.HttpServlet))

;; Reference to our application code
(defonce the-app (atom nil))

(defn resolve-and-start-app
  "This is a workaround for the recursive AOT compilation in Clojure. By
   dynamically requiring and resolving, the rest of the application is not
   AOT compiled."
  []
  (require 'bestinclass.admin)
  (reset! the-app (var-get (ns-resolve (the-ns 'bestinclass.admin) 'wroutes))))

(def app (delay (resolve-and-start-app)))

(ring.util.servlet/defservice @app)