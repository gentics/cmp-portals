{{/* vim: set filetype=mustache: */}}
{{/*
Expand the name of the chart.
*/}}
{{- define "gentics-portal.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "gentics-portal.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "gentics-portal.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Common labels
*/}}
{{- define "gentics-portal.labels" -}}
helm.sh/chart: {{ include "gentics-portal.chart" . }}
{{ include "gentics-portal.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{/*
Selector labels
*/}}
{{- define "gentics-portal.selectorLabels" -}}
app.kubernetes.io/name: {{ include "gentics-portal.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{/*
Create the name of the service account to use
*/}}
{{- define "gentics-portal.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
    {{ default (include "gentics-portal.fullname" .) .Values.serviceAccount.name }}
{{- else -}}
    {{ default "default" .Values.serviceAccount.name }}
{{- end -}}
{{- end -}}

{{/*
Workload controller kind — "Deployment" (default) or "StatefulSet".
The portal is stateless (the only persistent volume is the shared RWX claim,
mounted identically by every pod), so a Deployment is the correct fit. The
StatefulSet is kept for installations created before it became the default;
those must pin controller.kind=StatefulSet, because switching an existing
release means the controller is deleted and recreated.
*/}}
{{- define "gentics-portal.controllerKind" -}}
{{- $kind := (.Values.controller).kind | default "Deployment" -}}
{{- if not (has $kind (list "Deployment" "StatefulSet")) -}}
{{- fail (printf "controller.kind must be \"Deployment\" or \"StatefulSet\", got %q" $kind) -}}
{{- end -}}
{{- $kind -}}
{{- end -}}

{{/*
Create the name Mesh secret
*/}}
{{- define "gentics-portal.mesh.secretName" -}}
{{- if .Values.mesh.secretName -}}
{{- .Values.mesh.secretName -}}
{{- else -}}
{{ .Release.Name }}-mesh-secret
{{- end -}}
{{- end -}}

{{/*
Gentics Mesh host/port the portal connects to.
Priority: explicit mesh.host/mesh.port -> umbrella Mesh service ("<release>-mesh":80,
requires global.mesh.enabled) -> required error.
*/}}
{{- define "gentics-portal.meshHost" -}}
{{- include "gentics-common.meshServiceHost" (dict "root" . "local" .Values.mesh.host) -}}
{{- end -}}

{{- define "gentics-portal.meshPort" -}}
{{- include "gentics-common.meshServicePort" (dict "root" . "local" .Values.mesh.port) -}}
{{- end -}}

{{/*
Fully qualified Portal image (registry/repository:tag, global.imageRegistry aware).
image.repository stays required — there is no pre-built portal image.

Exception: the CMP umbrella's example installation (global.example.enabled) ships a
ready-made reference portal, so global.example.portalJavaImage replaces image.* and
the required check is skipped — an example install has no portal image of its own.
*/}}
{{- define "gentics-portal.image" -}}
{{- $example := ((.Values.global | default dict).example) | default dict -}}
{{- if $example.enabled -}}
{{- $exampleImage := $example.portalJavaImage | default dict -}}
{{- $_ := required "global.example.enabled is true but global.example.portalJavaImage.repository is not set." $exampleImage.repository -}}
{{- include "gentics-common.image" (dict "root" . "image" $exampleImage) -}}
{{- else -}}
{{- $_ := required "portal-java has no pre-built image. Build your portal image and set image.repository (see README)." .Values.image.repository -}}
{{- include "gentics-common.image" (dict "root" . "image" .Values.image) -}}
{{- end -}}
{{- end -}}

{{/*
Image pull secrets block for the pod spec. Four states, resolved by gentics-common:
  imagePullSecrets set and non-empty -> exactly those; global is ignored, never merged
  imagePullSecrets set but empty ([]) -> nothing emitted (explicit opt-out)
  imagePullSecrets absent            -> global.imagePullSecrets
  nothing set anywhere               -> default "docker-gentics-com", overridable via
                                        global.defaultImagePullSecret / defaultImagePullSecret
Distinguishing "empty" from "absent" is the point, so values.yaml must NOT ship an
"imagePullSecrets: []" default — that is the opt-out state, not "unset".
*/}}
{{- define "gentics-portal.imagePullSecrets" -}}
{{- include "gentics-common.imagePullSecretsList" (dict "root" . "localList" .Values.imagePullSecrets) -}}
{{- end }}
