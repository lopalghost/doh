(ns doh.core-test
  (:require [clojure.test :refer :all]
            [clojure.spec :as s]
            [doh.core :refer :all]))

(use-fixtures :each (fn [f]
                      (def test-state (atom []))
                      (f)))

(deftest def-err-test
  (testing "`def-err' defines a multimethod"
    (is (instance? clojure.lang.MultiFn
                   (def-err ::bad-error
                     [ctx]
                     :ret-spec (s/spec string?)
                     :retry (:string ctx)
                     :on-fail (swap! test-state conj (:string ctx))
                     :return :fail)))
    (is (instance? clojure.lang.MultiFn
                   (def-err ::no-retry
                     [ctx]
                     :on-fail (swap! test-state conj (:string ctx))
                     :return :fail)))
    (is (instance? clojure.lang.MultiFn
                   (def-err ::no-return
                     [ctx]
                     :ret-spec (s/spec string?)
                     :retry (:string ctx)
                     :on-fail (swap! test-state conj (:string ctx))))))
  (testing "Error handler returns retry value when valid"
    (is (= "A string"
           (handle-error ::bad-error {:string "A string"}))))
  (testing "Error handler returns fail value when retry invalid."
    (is (= :fail
           (handle-error ::bad-error {:string 1}))
        "Returns specified fail value.")
    (is (nil?
         (handle-error ::no-return {:string 1}))
        "Returns nil by default."))
  (testing "Failure handler executes properly"
    (is (= 1
           (let [_ (handle-error ::bad-error {:string 1})]
             (peek @test-state)))))
  (testing "Handler args allow destructuring"
    (is (= "Destructured"
           (do (def-err ::destructure
                 [{:keys [string] :as ctx}]
                 :ret-spec (s/spec string?)
                 :retry string)
               (handle-error ::destructure {:string "Destructured"})))))
  (testing "Spec passed into context"
    (is (= "a spec"
           (do (def-err ::spec-handler
                 [ctx]
                 :ret-spec (s/spec string?)
                 :retry (:string ctx)
                 :on-fail (swap! test-state conj
                                 (s/conform (:ret-spec ctx (s/spec number?)) "a spec")))
               (handle-error ::spec-handler {})
               (peek @test-state))))))


