oc create secret generic git-credentials \
  --from-literal=username=<git-username> \
  --from-literal=password=<git-pat>