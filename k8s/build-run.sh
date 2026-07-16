oc apply -f k8s/postgres-buildconfig-git.yaml
oc start-build postgres-rootless --follow