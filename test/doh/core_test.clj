(ns doh.core-test
  (:require [clojure.test :refer :all]
            [clojure.spec :as s]
            [doh.core :refer :all]))

(declare test-state)
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
                                 (s/conform (:doh/ret-spec ctx (s/spec number?)) "a spec")))
               (handle-error ::spec-handler {})
               (peek @test-state))))))

(deftest handle-exception-test
  (testing "Catches and handles an exception"
    (is (= "an exception"
           (do (def-err ::exception
                 [ctx]
                 :ret-spec (s/spec string?)
                 :retry (-> ctx :doh/exception ex-data :message))
               (handle-exception
                ::exception
                {}
                (throw (ex-info "Exception!" {:message "an exception"})))))))
  (testing "Catches and handles multiple exceptions"
    (do (def-err ::null-pointer
          [ctx]
          :ret-spec (s/spec string?)
          :retry "null pointer exception")
        (def-err ::divide-by-zero
          [ctx]
          :ret-spec (s/spec string?)
          :retry "arithmetic exception"))
    (is (= "null pointer exception"
           (handle-exception {NullPointerException ::null-pointer
                              ArithmeticException ::divide-by-zero}
                             {}
                             (/ 42 nil))))
    (is (= "arithmetic exception"
           (handle-exception {NullPointerException ::null-pointer
                              ArithmeticException ::divide-by-zero}
                             {}
                             (/ 42 0))))))


