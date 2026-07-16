oc apply -f k8s/postgres-buildconfig.yaml
oc start-build postgres-rootless --follow
oc apply -f k8s/postgres-deployment-service.yaml
oc rollout status deployment/postgres-it --timeout=180s