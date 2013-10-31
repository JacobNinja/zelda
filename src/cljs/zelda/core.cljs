(ns zelda.core
  (:require [cljs.core.async :as async 
             :refer [<! >! chan timeout alts!]]
            [clojure.browser.event :as event]
            [goog.events.KeyHandler]
            [goog.events.KeyCodes :as key-codes]
            [zelda.window])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def keyboard (goog.events.KeyHandler. js/document))
(def keyboard-chan (chan (async/sliding-buffer 1)))
(def valid-keys {key-codes/LEFT :left
                 key-codes/RIGHT :right
                 key-codes/UP :up
                 key-codes/DOWN :down})

(defn- keyboard-loop []
  (event/listen keyboard 
                "key"
                (fn [k]
                  (when-let [key (valid-keys (.-keyCode k))]
                    (go (>! keyboard-chan key))))))

(defn- adjust-player [env key]
  (let [[x y] (env :player)
        next-coord (condp = key
                     :right [(inc x) y]
                     :left [(dec x) y]
                     :up [x (dec y)]
                     :down [x (inc y)]
                     [x y])]
    (assoc env :player next-coord)))

(defn- init-env []
  {:player [5 5]
   :obstacles [[8 8]]})

(defn- game-loop [draw]
  (go
   (loop [env (init-env)]
     (>! draw env)
     (let [[keyboard-check _] (alts! [keyboard-chan (timeout 1)])
           next-env (-> env
                        (adjust-player keyboard-check))]
       (<! (timeout 200))
       (recur next-env)))))

(defn ^:export init []
  (let [draw-chan (chan)]
    (zelda.window/init draw-chan)
    (keyboard-loop)
    (game-loop draw-chan)))
