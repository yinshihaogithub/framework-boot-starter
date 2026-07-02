import axios from 'axios'

export const AUTH_EXPIRED_EVENT = 'admin-auth-expired'
const TRACE_ID_HEADER = 'X-Trace-Id'

export class ApiError extends Error {
  code?: number
  status?: number
  traceId?: string

  constructor(message: string, code?: number, status?: number, traceId?: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.traceId = traceId
  }
}

export interface ApiResult<T> {
  code: number
  message: string
  data: T
  timestamp: number
  traceId?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
  pages: number
}

export interface DashboardSummary {
  mq: Record<string, number>
  localMessage: Record<string, number>
  logs: Record<string, number>
  notifications: Record<string, number>
  excel: Record<string, number>
  files: Record<string, number>
  security: { defaultPasswordChanged: boolean }
  modules: Array<{ name: string; status: string }>
}

export interface MqStats {
  pendingCount: number
  retryingCount: number
  successCount: number
  exhaustedCount: number
  totalCount: number
  queues: MqQueueInfo[]
  runtime?: MqRuntimeInfo
}

export interface MqRuntimeInfo {
  enabled: boolean
  provider: string
  deadLetterEnabled: boolean
  retryAvailable: boolean
  deadLetterQueue?: string
  deadLetterRestoreLimit: number
  maxRetry: number
  retryFixedDelay: number
  failedMessageTableName?: string
  providers: MqProviderStatus[]
}

export interface MqProviderStatus {
  provider: string
  active: boolean
  available: boolean
}

export interface MqQueueInfo {
  queueName: string
  messageCount: number
  consumerCount: number
  state: string
}

export interface MqFailedMessage {
  id: number
  messageId: string
  traceId?: string
  parentMessageId?: string
  businessKey?: string
  messageType?: string
  exchange?: string
  routingKey?: string
  queueName?: string
  payload?: string
  errorMessage?: string
  errorStack?: string
  retryCount?: number
  maxRetry?: number
  status?: string
  source?: string
  tenantId?: string
  nextRetryTime?: string
  createTime?: string
  updateTime?: string
  operator?: string
  compensateRemark?: string
}

export interface ManualRetryResult {
  total: number
  success: number
  failed: number
  failedMessages: string[]
}

export interface BatchActionResult {
  total: number
  success: number
  failed: number
  failedMessages: string[]
}

export interface LocalMessage {
  id: number
  messageId: string
  traceId?: string
  parentMessageId?: string
  topic?: string
  businessKey?: string
  tenantId?: string
  operator?: string
  source?: string
  status?: string
  retryCount?: number
  maxRetry?: number
  nextRetryTime?: string
  payload?: string
  errorMessage?: string
  createTime?: string
  updateTime?: string
}

export interface OperationLog {
  id: number
  logType?: string
  module?: string
  action?: string
  operationType?: string
  uri?: string
  httpMethod?: string
  method?: string
  params?: string
  result?: string
  success?: boolean
  errorMessage?: string
  elapsedMs?: number
  operatorId?: number
  operatorName?: string
  clientIp?: string
  traceId?: string
  createTime?: string
}

export interface LoginLog {
  id: number
  username?: string
  userId?: number
  clientIp?: string
  success?: boolean
  message?: string
  createTime?: string
}

export interface OnlineSession {
  userId: number
  username?: string
  tenantId?: string
  deviceId: string
  loginTime: number
  ttlSeconds: number
}

export interface MenuItem {
  id: number
  parentId: number
  menuType: string
  menuName: string
  routePath?: string
  component?: string
  permission?: string
  icon?: string
  sortOrder?: number
  visible?: boolean
  children?: MenuItem[]
}

export interface CurrentUser {
  userId: number
  username: string
  nickname?: string
  tenantId?: number
  roles: string[]
  permissions: string[]
  menus?: MenuItem[]
}

export interface LoginResponse {
  accessToken: string
  user: CurrentUser
  menus: MenuItem[]
}

export interface AdminUser {
  id: number
  username: string
  nickname?: string
  mobile?: string
  email?: string
  status?: string
  deptId?: number
  roleIds?: number[]
  roles?: string[]
  lastLoginTime?: string
  createTime?: string
  loginFailCount?: number
  loginLocked?: boolean
  loginLockTtlMinutes?: number
}

export interface Role {
  id: number
  roleCode: string
  roleName: string
  status: string
  sortOrder: number
}

export interface Tenant {
  id: number
  tenantCode: string
  tenantName: string
  status: string
  createTime?: string
}

export interface Dept {
  id: number
  tenantId: number
  parentId: number
  deptName: string
  sortOrder: number
  status: string
  createTime?: string
  children?: Dept[]
}

export interface DictType {
  id: number
  dictCode: string
  dictName: string
  status: string
}

export interface DictItem {
  id: number
  dictCode: string
  itemLabel: string
  itemValue: string
  sortOrder: number
  status: string
}

export interface ConfigItem {
  id: number
  configKey: string
  configName: string
  configValue: string
  sensitive: boolean
  remark?: string
}

export interface TraceEvent {
  source: string
  title: string
  status?: string
  message?: string
  businessKey?: string
  time?: string
}

export interface TraceDetail {
  traceId: string
  summary: Record<string, number>
  displayed: Record<string, number>
  truncated: Record<string, boolean>
  limit: number
  warnings: string[]
  timeline: TraceEvent[]
  logs: OperationLog[]
  mqMessages: MqFailedMessage[]
  localMessages: LocalMessage[]
}

export interface HealthStatus {
  status: string
  components: Record<string, HealthComponent>
}

export interface HealthComponent {
  status: string
  details?: Record<string, unknown>
}

export interface NotifyTemplate {
  id: number
  templateCode: string
  templateName: string
  channel: string
  title: string
  content: string
  receivers: string[]
  webhookUrl?: string
  status: string
  createTime?: string
  updateTime?: string
}

export interface NotifyRecord {
  id: number
  templateCode?: string
  channel?: string
  title?: string
  content?: string
  receivers?: string[]
  webhookUrl?: string
  success?: boolean
  resultMessage?: string
  traceId?: string
  operatorName?: string
  createTime?: string
}

export interface ExcelTask {
  id: number
  taskName: string
  taskType: string
  bizType?: string
  status: string
  filename?: string
  totalRows: number
  successRows: number
  failureRows: number
  operatorName?: string
  errorMessage?: string
  createTime?: string
  updateTime?: string
}

export interface ExcelErrorRecord {
  id: number
  taskId: number
  rowIndex: number
  errorMessage?: string
  rawData?: string
  createTime?: string
}

export interface ExcelTaskResult {
  taskId: number
  filename?: string
  status: string
  totalRows: number
  successRows: number
  failureRows: number
  fileSize: number
}

export interface FileRecord {
  id: number
  fileKey: string
  originalFilename: string
  contentType?: string
  fileSize: number
  url?: string
  storageType?: string
  businessType?: string
  businessKey?: string
  operatorId?: number
  operatorName?: string
  deleted?: boolean
  createTime?: string
  updateTime?: string
}

const http = axios.create({
  baseURL: '',
  timeout: 10000
})
const BOUNDARY_SPACE_PATTERN = /^[\s\u00A0\u1680\u2000-\u200A\u202F\u205F\u3000]+|[\s\u00A0\u1680\u2000-\u200A\u202F\u205F\u3000]+$/g

export function getToken() {
  return localStorage.getItem('admin_token') ?? ''
}

export function setToken(token: string) {
  localStorage.setItem('admin_token', token)
}

export function clearToken() {
  localStorage.removeItem('admin_token')
}

export function isAuthExpiredError(error: unknown) {
  return error instanceof ApiError
    && (error.status === 401 || error.code === 401 || error.code === 2001 || error.code === 2002)
}

export function trimBoundarySpace(value: string) {
  return value.replace(BOUNDARY_SPACE_PATTERN, '')
}

export function trimToUndefined(value: unknown): string | undefined {
  if (typeof value !== 'string') {
    return undefined
  }
  const text = trimBoundarySpace(value)
  return text ? text : undefined
}

function notifyAuthExpired(error: ApiError) {
  clearToken()
  window.dispatchEvent(new CustomEvent<ApiError>(AUTH_EXPIRED_EVENT, { detail: error }))
}

let fallbackTraceSequence = 0

function bytesToHex(bytes: Uint8Array) {
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')
}

function paddedHex(value: number, length: number) {
  return Math.max(0, value).toString(16).padStart(length, '0').slice(-length)
}

function createTraceId() {
  const crypto = globalThis.crypto
  if (crypto?.randomUUID) {
    return crypto.randomUUID().replace(/-/g, '')
  }
  if (crypto?.getRandomValues) {
    const bytes = new Uint8Array(16)
    crypto.getRandomValues(bytes)
    return bytesToHex(bytes)
  }
  fallbackTraceSequence = (fallbackTraceSequence + 1) % Number.MAX_SAFE_INTEGER
  return [
    paddedHex(Date.now(), 12),
    paddedHex(Math.trunc(globalThis.performance?.now() ?? 0), 8),
    paddedHex(fallbackTraceSequence, 12)
  ].join('')
}

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  if (!config.headers[TRACE_ID_HEADER]) {
    config.headers[TRACE_ID_HEADER] = createTraceId()
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (axios.isAxiosError(error) && error.response) {
      const data = await parseErrorPayload(error.response.data)
      const apiError = new ApiError(
        data?.message || error.message || '请求失败',
        data?.code,
        error.response.status,
        data?.traceId
      )
      if (isAuthExpiredError(apiError)) {
        notifyAuthExpired(apiError)
      }
      return Promise.reject(apiError)
    }
    return Promise.reject(error)
  }
)

async function parseErrorPayload(data: unknown): Promise<Partial<ApiResult<unknown>> | undefined> {
  if (data instanceof Blob) {
    if (data.size <= 0) {
      return undefined
    }
    return parseApiResultText(await data.text())
  }
  if (typeof data === 'string') {
    return parseApiResultText(data)
  }
  return isApiResultLike(data) ? data : undefined
}

function parseApiResultText(text: string): Partial<ApiResult<unknown>> | undefined {
  if (!trimBoundarySpace(text)) {
    return undefined
  }
  try {
    const parsed: unknown = JSON.parse(text)
    return isApiResultLike(parsed) ? parsed : undefined
  } catch {
    return undefined
  }
}

function isApiResultLike(value: unknown): value is Partial<ApiResult<unknown>> {
  return typeof value === 'object' && value !== null
}

function unwrap<T>(response: ApiResult<T>): T {
  if (response.code !== 200) {
    const error = new ApiError(response.message, response.code, undefined, response.traceId)
    if (isAuthExpiredError(error)) {
      notifyAuthExpired(error)
    }
    throw error
  }
  return response.data
}

async function getData<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const response = await http.get<ApiResult<T>>(url, { params: normalizeQueryParams(params) })
  return unwrap(response.data)
}

async function postData<T>(url: string, data?: unknown, params?: Record<string, unknown>): Promise<T> {
  const response = await http.post<ApiResult<T>>(url, data, { params: normalizeQueryParams(params) })
  return unwrap(response.data)
}

async function putData<T>(url: string, data?: unknown, params?: Record<string, unknown>): Promise<T> {
  const response = await http.put<ApiResult<T>>(url, data, { params: normalizeQueryParams(params) })
  return unwrap(response.data)
}

async function deleteData<T>(url: string): Promise<T> {
  const response = await http.delete<ApiResult<T>>(url)
  return unwrap(response.data)
}

async function postForm<T>(url: string, data: FormData): Promise<T> {
  const response = await http.post<ApiResult<T>>(url, data)
  return unwrap(response.data)
}

async function getBlob(url: string): Promise<Blob> {
  const response = await http.get<Blob>(url, { responseType: 'blob' })
  return response.data
}

function normalizeQueryParams(params?: Record<string, unknown>) {
  if (!params) {
    return undefined
  }
  const normalized: Record<string, unknown> = {}
  for (const [key, value] of Object.entries(params)) {
    const nextValue = normalizeQueryValue(value)
    if (nextValue !== undefined) {
      normalized[key] = nextValue
    }
  }
  return Object.keys(normalized).length > 0 ? normalized : undefined
}

function normalizeQueryValue(value: unknown): unknown {
  if (value === undefined || value === null) {
    return undefined
  }
  if (typeof value === 'string') {
    return trimToUndefined(value)
  }
  if (Array.isArray(value)) {
    const items = value
      .map((item) => normalizeQueryValue(item))
      .filter((item) => item !== undefined)
    return items.length > 0 ? items : undefined
  }
  return value
}

export const api = {
  login: (data: { username: string; password: string; deviceId?: string }) =>
    postData<LoginResponse>('/admin/auth/login', data),
  me: () => getData<CurrentUser>('/admin/auth/me'),
  logout: () => postData<string>('/admin/auth/logout'),
  changePassword: (data: { oldPassword: string; newPassword: string }) =>
    putData<string>('/admin/auth/password', data),
  dashboard: () => getData<DashboardSummary>('/admin/dashboard'),
  mqStats: () => getData<MqStats>('/admin/mq/stats'),
  mqFailedMessages: (params: Record<string, unknown>) =>
    getData<PageResult<MqFailedMessage>>('/admin/mq/failed-messages', params),
  mqFailedMessage: (id: number) => getData<MqFailedMessage>(`/admin/mq/failed-messages/${id}`),
  retryMqMessage: (id: number, operator = 'admin', remark = '') =>
    postData<string>(`/admin/mq/failed-messages/${id}/retry`, null, { operator, remark }),
  batchRetryMqMessages: (ids: number[], operator = 'admin', remark = '') =>
    postData<ManualRetryResult>('/admin/mq/failed-messages/batch-retry', { ids, operator, remark }),
  manualSuccessMqMessage: (id: number, data: Record<string, unknown>) =>
    postData<string>(`/admin/mq/failed-messages/${id}/manual-success`, data),
  manualFailureMqMessage: (id: number, data: Record<string, unknown>) =>
    postData<string>(`/admin/mq/failed-messages/${id}/manual-failure`, data),
  deleteMqMessage: (id: number) => deleteData<string>(`/admin/mq/failed-messages/${id}`),
  cleanMqProcessed: () => deleteData<string>('/admin/mq/failed-messages/clean'),
  mqQueues: () => getData<MqQueueInfo[]>('/admin/mq/queues'),
  localMessageStats: () => getData<Record<string, number>>('/admin/local-messages/stats'),
  localMessages: (params: Record<string, unknown>) =>
    getData<PageResult<LocalMessage>>('/admin/local-messages', params),
  localMessage: (id: number) => getData<LocalMessage>(`/admin/local-messages/${id}`),
  retryDueLocalMessages: () => postData<number>('/admin/local-messages/retry-due'),
  batchRetryLocalMessages: (ids: number[]) =>
    postData<BatchActionResult>('/admin/local-messages/batch-retry', { ids }),
  retryLocalMessage: (id: number) => postData<string>(`/admin/local-messages/${id}/retry`),
  batchMarkLocalMessagesSuccess: (ids: number[]) =>
    postData<BatchActionResult>('/admin/local-messages/batch-success', { ids }),
  markLocalMessageSuccess: (id: number) => postData<string>(`/admin/local-messages/${id}/success`),
  batchMarkLocalMessagesFailure: (ids: number[], reason: string) =>
    postData<BatchActionResult>('/admin/local-messages/batch-failure', { ids, reason }),
  markLocalMessageFailure: (id: number, reason: string) =>
    postData<string>(`/admin/local-messages/${id}/failure`, { reason }),
  deleteLocalMessage: (id: number) => deleteData<string>(`/admin/local-messages/${id}`),
  cleanLocalProcessed: () => deleteData<string>('/admin/local-messages/clean'),
  logs: (params: Record<string, unknown>) => getData<PageResult<OperationLog>>('/admin/logs', params),
  loginLogs: (params: Record<string, unknown>) => getData<PageResult<LoginLog>>('/admin/logs/login', params),
  sessions: (params: Record<string, unknown>) => getData<PageResult<OnlineSession>>('/admin/sessions', params),
  kickSession: (userId: number, deviceId: string) =>
    deleteData<string>(`/admin/sessions/${userId}/${encodeURIComponent(deviceId)}`),
  traceLogs: (traceId: string) => getData<PageResult<OperationLog>>(`/admin/logs/traces/${traceId}`),
  traceDetail: (traceId: string) => getData<TraceDetail>(`/admin/traces/${traceId}`),
  monitorHealth: () => getData<HealthStatus>('/admin/monitor/health'),
  monitorJvm: () => getData<Record<string, unknown>>('/admin/monitor/jvm'),
  notifyStats: () => getData<Record<string, number>>('/admin/notify/stats'),
  notifyTemplates: (params: Record<string, unknown>) =>
    getData<PageResult<NotifyTemplate>>('/admin/notify/templates', params),
  createNotifyTemplate: (data: Record<string, unknown>) => postData<number>('/admin/notify/templates', data),
  updateNotifyTemplate: (id: number, data: Record<string, unknown>) =>
    putData<string>(`/admin/notify/templates/${id}`, data),
  deleteNotifyTemplate: (id: number) => deleteData<string>(`/admin/notify/templates/${id}`),
  sendTestNotify: (id: number, data: Record<string, unknown>) =>
    postData<NotifyRecord>(`/admin/notify/templates/${id}/send-test`, data),
  notifyRecords: (params: Record<string, unknown>) =>
    getData<PageResult<NotifyRecord>>('/admin/notify/records', params),
  excelStats: () => getData<Record<string, number>>('/admin/excel/stats'),
  excelTasks: (params: Record<string, unknown>) => getData<PageResult<ExcelTask>>('/admin/excel/tasks', params),
  createExportTask: (data: Record<string, unknown>) =>
    postData<ExcelTaskResult>('/admin/excel/tasks/export', data),
  createImportFailureTask: (data: Record<string, unknown>) =>
    postData<ExcelTaskResult>('/admin/excel/tasks/import-failure', data),
  excelErrors: (taskId: number, params: Record<string, unknown>) =>
    getData<PageResult<ExcelErrorRecord>>(`/admin/excel/tasks/${taskId}/errors`, params),
  fileStats: () => getData<Record<string, number>>('/admin/files/stats'),
  files: (params: Record<string, unknown>) => getData<PageResult<FileRecord>>('/admin/files', params),
  uploadFile: (data: FormData) => postForm<FileRecord>('/admin/files', data),
  downloadFile: (id: number) => getBlob(`/admin/files/${id}/download`),
  deleteFile: (id: number) => deleteData<string>(`/admin/files/${id}`),
  users: (params: Record<string, unknown>) => getData<PageResult<AdminUser>>('/admin/system/users', params),
  tenants: (params: Record<string, unknown>) => getData<PageResult<Tenant>>('/admin/system/tenants', params),
  tenantOptions: (params?: Record<string, unknown>) => getData<Tenant[]>('/admin/system/tenant-options', params),
  createTenant: (data: Record<string, unknown>) => postData<number>('/admin/system/tenants', data),
  updateTenant: (id: number, data: Record<string, unknown>) => putData<string>(`/admin/system/tenants/${id}`, data),
  deleteTenant: (id: number) => deleteData<string>(`/admin/system/tenants/${id}`),
  depts: (tenantId?: number) => getData<Dept[]>('/admin/system/depts', { tenantId }),
  createDept: (data: Record<string, unknown>) => postData<number>('/admin/system/depts', data),
  updateDept: (id: number, data: Record<string, unknown>) => putData<string>(`/admin/system/depts/${id}`, data),
  deleteDept: (id: number) => deleteData<string>(`/admin/system/depts/${id}`),
  roles: (params: Record<string, unknown>) => getData<PageResult<Role>>('/admin/system/roles', params),
  roleOptions: (params?: Record<string, unknown>) => getData<Role[]>('/admin/system/role-options', params),
  createRole: (data: Record<string, unknown>) => postData<number>('/admin/system/roles', data),
  updateRole: (id: number, data: Record<string, unknown>) => putData<string>(`/admin/system/roles/${id}`, data),
  deleteRole: (id: number) => deleteData<string>(`/admin/system/roles/${id}`),
  roleMenuIds: (id: number) => getData<number[]>(`/admin/system/roles/${id}/menu-ids`),
  updateRoleMenus: (id: number, menuIds: number[]) => putData<string>(`/admin/system/roles/${id}/menus`, { menuIds }),
  menus: () => getData<MenuItem[]>('/admin/system/menus'),
  createMenu: (data: Record<string, unknown>) => postData<number>('/admin/system/menus', data),
  updateMenu: (id: number, data: Record<string, unknown>) => putData<string>(`/admin/system/menus/${id}`, data),
  deleteMenu: (id: number) => deleteData<string>(`/admin/system/menus/${id}`),
  dictTypes: (params: Record<string, unknown>) => getData<PageResult<DictType>>('/admin/system/dict-types', params),
  dictTypeOptions: (params?: Record<string, unknown>) => getData<DictType[]>('/admin/system/dict-type-options', params),
  createDictType: (data: Record<string, unknown>) => postData<number>('/admin/system/dict-types', data),
  updateDictType: (id: number, data: Record<string, unknown>) => putData<string>(`/admin/system/dict-types/${id}`, data),
  deleteDictType: (id: number) => deleteData<string>(`/admin/system/dict-types/${id}`),
  dictItems: (params: Record<string, unknown>) => getData<PageResult<DictItem>>('/admin/system/dict-items', params),
  createDictItem: (data: Record<string, unknown>) => postData<number>('/admin/system/dict-items', data),
  updateDictItem: (id: number, data: Record<string, unknown>) => putData<string>(`/admin/system/dict-items/${id}`, data),
  deleteDictItem: (id: number) => deleteData<string>(`/admin/system/dict-items/${id}`),
  configs: (params: Record<string, unknown>) => getData<PageResult<ConfigItem>>('/admin/system/configs', params),
  createConfig: (data: Record<string, unknown>) => postData<number>('/admin/system/configs', data),
  updateConfig: (id: number, data: Record<string, unknown>) => putData<string>(`/admin/system/configs/${id}`, data),
  deleteConfig: (id: number) => deleteData<string>(`/admin/system/configs/${id}`),
  createUser: (data: Record<string, unknown>) => postData<number>('/admin/system/users', data),
  updateUser: (id: number, data: Record<string, unknown>) => putData<string>(`/admin/system/users/${id}`, data),
  updateUserStatus: (id: number, status: string) => putData<string>(`/admin/system/users/${id}/status`, { status }),
  resetPassword: (id: number, password: string) => putData<string>(`/admin/system/users/${id}/password`, { password }),
  unlockUser: (id: number) => putData<string>(`/admin/system/users/${id}/unlock`),
  deleteUser: (id: number) => deleteData<string>(`/admin/system/users/${id}`)
}
