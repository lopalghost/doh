(ns doh.core
  (:require [clojure.spec :as s]))

(defmulti handle-error (fn [err-key _] err-key))

(defmacro def-err
  [err-key [args] & {:keys [ret-spec retry on-fail return] :or {ret-spec `(s/spec any?)}}]
  (let [[arg-body ctx-sym] (cond
                             (map? args) (if (:as args)
                                           [args (:as args)]
                                           [(assoc args :as 'ctx) 'ctx])
                             (symbol? args) [args args])]
    `(defmethod handle-error ~err-key
       [~'_ ~arg-body]
       (let [~ctx-sym (assoc ~ctx-sym :doh/ret-spec ~ret-spec)
             result# ~retry]
         (if (and result#
                  (s/valid? ~ret-spec result#))
           result#
           (do ~on-fail
               ~return))))))

(s/def ::ns-keyword (s/and keyword?
                           #(not-empty (namespace %))))

(s/def ::ret-spec (s/cat :key #(= :ret-spec %)
                         :body (s/or :spec #(s/spec? (eval %))
                                     :keyword #(s/spec? (s/get-spec (eval %))))))

(s/def ::retry (s/cat :key #(= :retry %)
                      :body any?))

(s/def ::on-fail (s/cat :key #(= :on-fail %)
                        :body any?))

(s/def ::return (s/cat :key #(= :return %)
                       :body any?))

(s/fdef def-err
        :args (s/cat :err-key ::ns-keyword
                     :args (s/spec (s/coll-of (s/or :symbol symbol?
                                                    :map map?)
                                              :kind vector?
                                              :count 1))
                     :opts (s/* (s/alt :ret-spec ::ret-spec
                                       :retry ::retry
                                       :on-fail ::on-fail
                                       :return ::return))))

(defmacro handle-exception
  [handler-body ctx & body]
  (let [catch-body (if (keyword? handler-body)
                     `((catch Throwable e#
                         (handle-error ~handler-body (assoc ~ctx :doh/exception e#))))
                     (for [[ex-type handler] handler-body]
                       `(catch ~ex-type e#
                          (handle-error ~handler (assoc ~ctx :doh/exception e#)))))]
    `(try ~@body
          ~@catch-body)))

(s/fdef handle-exception
        :args (s/cat :handler-body (s/or :single keyword?
                                         :many (s/map-of symbol? ::ns-keyword))
                     :ctx (s/or :symbol symbol?
                                :map map?)
                     :body (s/+ any?)))
