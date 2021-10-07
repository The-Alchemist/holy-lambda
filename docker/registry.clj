(require '[selmer.parser :as selm])
(require '[babashka.process :as p])

(defn- exit-non-zero
  [proc]
  (when-let [exit-code (some-> proc deref :exit)]
    (when (not (zero? exit-code))
      (System/exit exit-code))))

(defn- shell
  [cmd]
  (exit-non-zero (p/process (p/tokenize cmd) {:inherit true})))

(def CE_IMAGES
  [{:version ["21.2.0" "21.1.0"]
    :java ["11"]
    :arch ["amd64", "aarch64"]}
   {:version ["21.2.0" "21.1.0"]
    :java ["8"]
    :arch ["amd64"]}])

(def DEV_IMAGES
  [{:version "21.3.0-dev-20211006_0800"
    :java ["8" "11"]
    :arch ["amd64", "aarch64"]}])

(defn dev-image-url
  [{:keys [version java arch]}]
  (str "https://github.com/graalvm/graalvm-ce-dev-builds/releases/download/" version "/graalvm-ce-java" java "-linux-" arch "-dev.tar.gz"))

(defn build-pub-ce!
  [{:keys [java version arch] :as spec}]
  (let [variant             "ce"
        dockerfile          (str "Dockerfile" "." variant)
        dockerfile-template (str dockerfile ".template")
        dockerfile-content  (selm/render (slurp dockerfile-template)
                                         (assoc spec
                                                :image-prefix (if-not (= arch "aarch64") "" "arm64v8/")
                                                :additional-components (if (= arch "aarch64")
                                                                         ""
                                                                         " python ruby R")))
        image-uri           (str "ghcr.io/fierycod/holy-lambda-builder:" arch "-java" java "-" version)]
    (spit dockerfile dockerfile-content)
    (println "> Building:" image-uri)
    (shell (str "docker build . -f " dockerfile " -t " image-uri (when (= arch "aarch64") " --platform linux/aarch64")))
    (println "> Publishing:" image-uri)
    (shell (str "docker push " image-uri))))

(def requested-arch (first *command-line-args*))
(def CE (= (second *command-line-args*) "CE"))

(when CE
  (doseq [{:keys [version java arch]} CE_IMAGES]
    (doseq [version version]
      (doseq [java java]
        (doseq [arch arch]
          (when (= requested-arch arch)
            (build-pub-ce! {:version version
                            :java    java
                            :arch    arch})))))))

;; (when-not CE
;;   (doseq [image DEV_IMAGES]

;;     )
;;   )
