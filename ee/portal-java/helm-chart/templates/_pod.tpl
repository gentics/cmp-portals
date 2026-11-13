{{/* vim: set filetype=mustache: */}}
{{/*
Pod template (the "spec.template" block) shared by the Deployment and the
StatefulSet controller — see controller.kind in values.yaml. Both controllers
carry the pod template at the same depth, so this renders at its final
indentation: include it verbatim right below the controller's "spec:" key and
do NOT pipe it through nindent.
*/}}
{{- define "gentics-portal.podTemplate" }}
  template:
    metadata:
      labels:
        {{- include "gentics-portal.selectorLabels" . | nindent 8 }}
      {{- with .Values.podAnnotations }}
      annotations:
        {{- toYaml . | nindent 8}}
      {{- end }}
    spec:
    {{- with (include "gentics-portal.imagePullSecrets" .) }}
      {{- . | nindent 6 }}
    {{- end }}
      serviceAccountName: {{ include "gentics-portal.serviceAccountName" . }}
      securityContext:
        {{- toYaml .Values.podSecurityContext | nindent 8 }}
      terminationGracePeriodSeconds: 30
      volumes:
        - name: cache-volume
          emptyDir: {}
        - name: config
          configMap:
            {{- if .Values.existingConfigmap }}
            name: {{ .Values.existingConfigmap }}
            {{- else }}
            name: {{ template "gentics-portal.fullname" . }}-config
            {{- end }}
        - name: shared
        {{- if .Values.persistence.enabled }}
          persistentVolumeClaim:
            {{- if .Values.persistence.shared.existingClaim }}
            claimName: {{ .Values.persistence.shared.existingClaim }}
            {{- else }}
            claimName: {{ include "gentics-portal.fullname" . }}-shared
            {{- end }}
        {{- else }}
          emptyDir: {}
        {{- end }}

        {{- range .Values.extraVolumes }}
        - name: {{ .name }}
          {{- toYaml .volume | nindent 10 }}
        {{- end }}
      containers:
      - name: {{ .Chart.Name }}
        {{- if .Values.securityContext }}
        securityContext:
           {{- toYaml .Values.securityContext | nindent 12 }}
        {{- end }}
        image: "{{ include "gentics-portal.image" . }}"
        imagePullPolicy: {{ .Values.image.pullPolicy }}
        volumeMounts:
          {{- if .Values.keycloak.enabled }}
          - mountPath: "/portal/config/keycloak.json"
            name: config
            subPath: keycloak.json
            readOnly: true
          {{- end }}

          - mountPath: "/portal/config/cache.ccf"
            name: config
            subPath: cache.ccf
            readOnly: true

          - mountPath: "/portal/config/logback.xml"
            name: config
            subPath: logback.xml
            readOnly: true

          {{- if .Values.portalConfig }}
          - mountPath: "/portal/config/server.yml"
            name: config
            subPath: server.yml
            readOnly: true
          {{- end }}

          - mountPath: /portal/.response_cache
            name: cache-volume
          {{- range .Values.extraVolumes }}
          - name: {{ .name }}
            {{- toYaml .mount | nindent 12 }}
          {{- end }}
          {{- if .Values.extraVolumeMounts }}
          {{- range .Values.extraVolumeMounts }}
          - mountPath: {{ .mountPath }}
            name: {{ .name }}
            subPath: {{ .subPath }}
            readOnly: {{ .readOnly | default false }}
          {{- end }}
          {{- end }}

        env:
          ## Mesh Settings
          - name: MESH_API_KEY
          {{- if .Values.mesh.apiKeyFromSecret }}
            valueFrom:
                secretKeyRef:
                  name: {{ include "gentics-portal.mesh.secretName" . }}
                  key: {{ .Values.mesh.secretKey | default "apikey" | quote }}
          {{- else }}
            value: {{ required "A valid Gentics Mesh api key is required!" .Values.mesh.apikey | quote }}
          {{- end }}
          - name: MESH_HOST
            value: "{{ include "gentics-portal.meshHost" . }}"
          - name: MESH_PORT
            value: "{{ include "gentics-portal.meshPort" . }}"
          {{- if .Values.mesh.project }}
          - name: MESH_PROJECT
            value: "{{ .Values.mesh.project }}"
          {{- end }}
          - name: MESH_SSL
            value: "{{ .Values.mesh.ssl | default false }}"

          ## Monitoring
          - name: MONITORING_ENABLED
            value: "{{ .Values.monitoring.enabled }}"
          - name: MONITORING_HTTP_HOST
            value: "0.0.0.0"
          - name: MONITORING_HTTP_PORT
            value: "3001"
          - name: MONITORING_JMX_ENABLED
            value: "{{ .Values.monitoring.jmxEnabled }}"
          - name: MONITORING_CHECK_MESH_READY
            value: "{{ .Values.monitoring.ignoreMeshReady }}"

          ## Portal Server Settings
          - name: PORTAL_API_KEY
            value: "{{ .Values.portal.key }}"
          - name: SERVER_URL
            value: "{{ .Values.portal.server.url }}"
          - name: SERVER_PORT
            value: "{{ .Values.portal.server.port}}"
          - name: SERVER_HOST
            value: "{{ .Values.portal.server.host }}"
          - name: PORTAL_EVENT_POOL_SIZE
            value: "{{ .Values.portal.server.eventPoolSize }}"
          - name: PORTAL_WORKER_POOL_SIZE
            value: "{{ .Values.portal.server.workerPoolSize }}"

          ## Content
          - name: DEFAULT_LANGUAGE
            value: "{{ .Values.portal.lang }}"
          - name: COMPATIBILITY_PREVIEW
            value: "{{ .Values.portal.compatibilityPreview }}"
          - name: BREADCRUMBS_LEVEL
            value: "{{ .Values.portal.breadcrumbsLevel }}"
          {{- if .Values.portal.schemaPrefix }}
          - name: MESH_SCHEMA_PREFIX
            value: "{{ .Values.portal.schemaPrefix }}"
          {{- end }}

          ## Eventbus
          - name: MESH_EVENTBUS_PING_INTERVAL
            value: "{{ .Values.eventbus.pingInterval }}"
          - name: MESH_EVENTBUS_RETRY_TIMEOUT
            value: "{{ .Values.eventbus.retryTimeout }}"

          ## Navigation settings
          - name: NAV_BREADCRUMBS_LEVEL
            value: "{{ .Values.portal.nav.breadcrumbsLevel }}"
          - name: NAV_EXPIRE_TIME
            value: "{{ .Values.portal.nav.expireTime }}"
          - name: NAV_HIDDEN_FIELD
            value: "{{ .Values.portal.nav.hiddenField }}"
          - name: NAV_SORTORDER_FIELD
            value: "{{ .Values.portal.nav.sortorderField }}"

          ## Paths
          - name: TEMPLATES_PATH
            value: "{{ .Values.portal.path.templates }}"
          - name: STATIC_PATH
            value: "{{ .Values.portal.path.static }}"
          - name: I18N_PATH
            value: "{{ .Values.portal.path.i18n }}"
          - name: QUERY_PATH
            value: "{{ .Values.portal.path.queries }}"

          ## Caching Setting
          - name: CACHING_ENABLED
            value: "{{ .Values.portal.caching.enabled }}"
          - name: STATIC_CACHING
            value: "{{ .Values.portal.caching.static }}"
          - name: STATIC_MAX_AGE
            value: "{{ .Values.portal.caching.staticMaxAge }}"
          - name: CACHING_EXPIRE_TIME
            value: "{{ .Values.portal.caching.expireTime }}"
          - name: CACHING_MAXIMUM_SIZE
            value: "{{ .Values.portal.caching.maxSize }}"
          - name: CACHING_DISK_CACHE_REGION_NAME
            value: "{{ .Values.portal.caching.regionName }}"
          - name: CACHING_CONFIG_FILE
            value: "{{ .Values.portal.caching.config }}"

          ## Auth
          - name: AUTH_CALLBACK_ENDPOINT
            value: "{{ .Values.auth.callbackEndpoint }}"
          - name: AUTH_INSECURE_SSL
            value: "{{ .Values.auth.insecureSSL }}"
          - name: AUTH_LOGIN_ENDPOINT
            value: "{{ .Values.auth.loginEndpoint }}"
          - name: AUTH_LOGOUT_ENDPOINT
            value: "{{ .Values.auth.logoutEndpoint }}"
          - name: AUTH_LOGOUT_REDIRECT
            value: "{{ .Values.auth.logoutRedirect }}"
          - name: AUTH_REDIRECT_COOKIE_MAX_AGE
            value: "{{ .Values.auth.redirectCookieMaxAge }}"
          {{- with include "gentics-common.otelEnv" (dict "root" . "local" .Values.otel "extraEnv" .Values.extraEnv "defaultServiceName" "Gentics Portal Java" "defaultAgentPath" "") }}
          {{- . | nindent 10 }}
          {{- end }}

{{ toYaml .Values.extraEnv | indent 10 }}
          {{- /*
          CMP umbrella example installation: extra env for the reference project.
          Rendered last so it wins over the blocks above — Kubernetes takes the
          final entry when an env name appears more than once.
          */}}
          {{- $example := ((.Values.global | default dict).example) | default dict }}
          {{- if $example.enabled }}
          {{- with $example.env }}
          {{- /*
          Rendered through tpl so entries can reference the release context —
          e.g. value: "{{ `{{ .Release.Name }}` }}-cms" to reach a sibling
          component's Service or Secret. A literal "{{ `{{` }}" in any value
          must be escaped, or tpl tries to execute it.
          */}}
          {{- tpl (toYaml .) $ | nindent 10 }}
          {{- end }}
          {{- end }}

        ports:
          - name: http
            containerPort: 3000
            protocol: TCP
          - name: monitoring
            containerPort: 3001
            protocol: TCP

        livenessProbe:
          {{- toYaml .Values.livenessProbe | nindent 10 }}

        readinessProbe:
          {{- toYaml .Values.readinessProbe | nindent 10 }}

        startupProbe:
          {{- toYaml .Values.startupProbe | nindent 10 }}

        resources:
{{ toYaml .Values.resources | indent 12 }}

    {{- with .Values.nodeSelector }}
      nodeSelector:
        {{- toYaml . | nindent 8 }}
    {{- end }}
    {{- with .Values.tolerations }}
      tolerations:
        {{- toYaml . | nindent 8 }}
    {{- end }}
      {{- /*
      A non-empty .Values.affinity REPLACES the default below rather than being
      merged with it — merging emitted podAntiAffinity twice, and a duplicate
      mapping key is either silently collapsed or rejected outright.
      */}}
      affinity:
    {{- if .Values.affinity }}
        {{- toYaml .Values.affinity | nindent 8 }}
    {{- else }}
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
          - weight: 100
            podAffinityTerm:
              topologyKey: "kubernetes.io/hostname"
              labelSelector:
                matchExpressions:
                - key: "app.kubernetes.io/instance"
                  operator: In
                  values:
                  - {{ .Release.Name }}
    {{- end }}
{{- end }}
