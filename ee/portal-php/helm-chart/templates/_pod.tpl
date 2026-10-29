{{/* vim: set filetype=mustache: */}}
{{/*
Pod template (the "spec.template" block) shared by the Deployment and the
StatefulSet controller — see controller.kind in values.yaml. Both controllers
carry the pod template at the same depth, so this renders at its final
indentation: include it verbatim right below the controller's "spec:" key and
do NOT pipe it through nindent.
*/}}
{{- define "gentics-portal-php.podTemplate" }}
{{- $cacheMode := include "gentics-portal-php.persistentCacheMode" . }}
  template:
    metadata:
      labels:
        {{- include "gentics-portal-php.selectorLabels" . | nindent 8 }}
    spec:
    {{- with (include "gentics-portal-php.imagePullSecrets" .) }}
      {{- . | nindent 6 }}
    {{- end }}
      serviceAccountName: {{ include "gentics-portal-php.serviceAccountName" . }}
      securityContext:
        {{- toYaml .Values.podSecurityContext | nindent 8 }}
      terminationGracePeriodSeconds: 30
      volumes:
        {{- range .Values.extraVolumes }}
        - name: {{ .name }}
          {{- toYaml .volume | nindent 10 }}
        {{- end }}
        {{- if eq $cacheMode "claim" }}
        - name: data
          persistentVolumeClaim:
            claimName: {{ .Values.persistence.data.existingClaim }}
        {{- end }}
        - name: shared
        {{- if .Values.persistence.enabled }}
          persistentVolumeClaim:
          {{- if (.Values.persistence.shared.existingClaim) }}
            claimName: {{ .Values.persistence.shared.existingClaim }}
          {{- else }}
            claimName: shared-{{ include "gentics-portal-php.fullname" . }}
          {{- end }}
        {{- else }}
          emptyDir: {}
        {{- end }}

        {{- if .Values.existingConfigmap }}
        - name: config
          configMap:
            name: {{ .Values.existingConfigmap }}
        {{- end }}
      containers:
      - name: {{ .Chart.Name }}
        {{- if .Values.securityContext }}
        securityContext:
           {{- toYaml .Values.securityContext | nindent 12 }}
        {{- end }}
        image: "{{ include "gentics-portal-php.image" . }}"
        imagePullPolicy: {{ .Values.image.pullPolicy }}

        env:
          ## Mesh Settings
          - name: MESH_APIKEY
          {{- if .Values.mesh.apikey }}
            value: {{ required "A valid Gentics Mesh api key is required!" .Values.mesh.apikey | quote }}
          {{- else }}
            valueFrom:
                secretKeyRef:
                  name: {{ include "gentics-portal-php.mesh.secretName" . }}
                  key: {{ .Values.mesh.secretKey | default "apikey" | quote }}
          {{- end }}
          - name: MESH_URL
            value: {{ include "gentics-portal-php.meshUrl" . | quote }}
          - name: AUTOGENERATE_MESH_API_KEY
            value: {{ .Values.mesh.autogenerate_apikey | default "false" | quote }}

{{- if .Values.extraEnv }}
{{ toYaml .Values.extraEnv | indent 10 }}
{{- end }}

        ports:
          - name: http
            containerPort: {{ default "80" .Values.httpPort }}
            protocol: TCP
          - name: https
            containerPort: {{ default "443" .Values.httpsPort }}
            protocol: TCP

        livenessProbe:
          httpGet:
            path: /api/health/live
            scheme: {{ default "HTTP" .Values.portalProbeScheme }}
            port: {{ default "http" .Values.portalProbePortName }}
            httpHeaders:
            - name: Host
              value: {{ default "portal" .Values.portalHost | quote }}
          periodSeconds: {{ default 15 .Values.liveness.period }}
          timeoutSeconds: {{ default 3 .Values.liveness.timeout }}
          failureThreshold: {{ default 2 .Values.liveness.failureThreshold }}
        readinessProbe:
          httpGet:
            path: /api/health/ready
            scheme: {{ default "HTTP" .Values.portalProbeScheme }}
            port: {{ default "http" .Values.portalProbePortName }}
            httpHeaders:
            - name: Host
              value: {{ default "portal" .Values.portalHost | quote }}
          periodSeconds: {{ default 15 .Values.readiness.period }}
          timeoutSeconds: {{ default 3 .Values.readiness.timeout }}
          failureThreshold: {{ default 2 .Values.readiness.failureThreshold }}

        resources:
{{ toYaml .Values.resources | indent 12 }}

        volumeMounts:
          {{- range .Values.extraVolumes }}
          - name: {{ .name }}
            {{- toYaml .mount | nindent 12 }}
          {{- end }}
          {{- if .Values.extraVolumeMounts }}
          {{- range .Values.extraVolumeMounts }}
          - mountPath: {{ .mountPath }}
            name: {{ .name }}
            subPath: {{ .subPath }}
          {{- end }}
          {{- end }}
        {{- if $cacheMode }}
          - mountPath: "/portal/bootstrap/cache"
            name: data
            subPath: "bootstrap-cache"
          - mountPath: "/portal/storage/framework/cache"
            name: data
            subPath: "storage-cache"
        {{- end }}
        {{- if not .Values.disableSessionsMount }}
          - mountPath: "/portal/storage/framework/sessions"
            name: shared
            subPath: "storage-sessions"
        {{- end }}
        {{- if .Values.existingConfigmap }}
          - mountPath: "/portal/config"
            name: config
        {{- end }}

    {{- with .Values.nodeSelector }}
      nodeSelector:
        {{- toYaml . | nindent 8 }}
    {{- end }}
    {{- with .Values.tolerations }}
      tolerations:
        {{- toYaml . | nindent 8 }}
    {{- end }}
      affinity:
    {{- with .Values.affinity }}
        {{- toYaml . | nindent 8 }}
    {{- end }}
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
            - weight: 100
              podAffinityTerm:
                labelSelector:
                  matchExpressions:
                    - key: app.kubernetes.io/instance
                      operator: In
                      values:
                        - "{{ .Release.Name }}"
                topologyKey: kubernetes.io/hostname
{{- end }}
