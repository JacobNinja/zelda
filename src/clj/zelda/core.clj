(ns zelda.core
  (:use [compojure.core]
        [hiccup.core])
  (:require [compojure.handler :as handler]
            [compojure.route :as route]))

(defn page [game-ns]
  (html
   [:head {:title "Zelda"}
    [:link {:rel "stylesheet" :href "style.css"}]]
   [:body {:onload (str "zelda." game-ns".init();")}
    [:div
     [:div {:id "player"}
      [:span {:id "hit-points"}
       [:img {:src "images/heart.png"}]]
      "&nbsp;"
      [:span "Inventory: " [:span {:id "inventory"}]]]
     [:div {:id "map" } [:canvas#world]]
     [:script {:src "js/dev.js"}]]]))

(defroutes app-routes
  (GET "/" [] (page "core"))
  (GET "/ui" [] (page "ui"))
  (route/resources "/")
  (route/not-found "Not found"))

(def app (handler/site app-routes))

