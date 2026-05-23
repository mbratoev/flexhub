{{/* Common labels applied to all rendered resources. */}}
{{- define "flexhub-service.labels" -}}
app: {{ .Values.name }}
app.kubernetes.io/name: {{ .Values.name }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
{{- end }}

{{/* Selector labels — must NOT change once the Deployment is created. */}}
{{- define "flexhub-service.selectorLabels" -}}
app: {{ .Values.name }}
{{- end }}
