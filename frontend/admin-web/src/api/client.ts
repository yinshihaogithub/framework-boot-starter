import axios from 'axios'

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
  deadLetterQueue?: string
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

export interface LocalMessage {
  id: number
  messageId: string
  traceId?: string
  topic?: string
  businessKey?: string
  status?: string
  retryCount?: number
  maxRetry?: number
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
  success?: boolean
  errorMessage?: string
  elapsedMs?: number
  operatorName?: string
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

const http = axios.create({
  baseURL: '',
  timeout: 10000
})

export function getToken() {
  return localStorage.getItem('admin_token') ?? ''
}

export function setToken(token: string) {
  localStorage.setItem('admin_token', token)
}

export function clearToken() {
  localStorage.removeItem('admin_token')
}

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

async function getData<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const response = await http.get<ApiResult<T>>(url, { params })
  if (response.data.code !== 200) {
    throw new Error(response.data.message)
  }
  return response.data.data
}

async function postData<T>(url: string, data?: unknown, params?: Record<string, unknown>): Promise<T> {
  const response = await http.post<ApiResult<T>>(url, data, { params })
  if (response.data.code !== 200) {
    throw new Error(response.data.message)
  }
  return response.data.data
}

async function putData<T>(url: string, data?: unknown, params?: Record<string, unknown>): Promise<T> {
  const response = await http.put<ApiResult<T>>(url, data, { params })
  if (response.data.code !== 200) {
    throw new Error(response.data.message)
  }
  return response.data.data
}

async function deleteData<T>(url: string): Promise<T> {
  const response = await http.delete<ApiResult<T>>(url)
  if (response.data.code !== 200) {
    throw new Error(response.data.message)
  }
  return response.data.data
}

export const api = {
  login: (data: { username: string; password: string; deviceId?: string }) =>
    postData<LoginResponse>('/admin/auth/login', data),
  me: () => getData<CurrentUser>('/admin/auth/me'),
  logout: () => postData<string>('/admin/auth/logout'),
  dashboard: () => getData<DashboardSummary>('/admin/dashboard'),
  mqStats: () => getData<MqStats>('/admin/mq/stats'),
  mqFailedMessages: (params: Record<string, unknown>) =>
    getData<PageResult<MqFailedMessage>>('/admin/mq/failed-messages', params),
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
  localMessages: (params: Record<string, unknown>) =>
    getData<PageResult<LocalMessage>>('/admin/local-messages', params),
  retryDueLocalMessages: () => postData<number>('/admin/local-messages/retry-due'),
  markLocalMessageSuccess: (id: number) => postData<string>(`/admin/local-messages/${id}/success`),
  markLocalMessageFailure: (id: number, reason: string) =>
    postData<string>(`/admin/local-messages/${id}/failure`, { reason }),
  deleteLocalMessage: (id: number) => deleteData<string>(`/admin/local-messages/${id}`),
  logs: (params: Record<string, unknown>) => getData<PageResult<OperationLog>>('/admin/logs', params),
  loginLogs: (params: Record<string, unknown>) => getData<PageResult<LoginLog>>('/admin/logs/login', params),
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
  createDemoExportTask: (data: Record<string, unknown>) =>
    postData<ExcelTaskResult>('/admin/excel/tasks/demo-export', data),
  createDemoFailureTask: (data: Record<string, unknown>) =>
    postData<ExcelTaskResult>('/admin/excel/tasks/demo-failure', data),
  excelErrors: (taskId: number) => getData<ExcelErrorRecord[]>(`/admin/excel/tasks/${taskId}/errors`),
  users: (params: Record<string, unknown>) => getData<PageResult<AdminUser>>('/admin/system/users', params),
  tenants: () => getData<Tenant[]>('/admin/system/tenants'),
  createTenant: (data: Record<string, unknown>) => postData<number>('/admin/system/tenants', data),
  updateTenant: (id: number, data: Record<string, unknown>) => putData<string>(`/admin/system/tenants/${id}`, data),
  deleteTenant: (id: number) => deleteData<string>(`/admin/system/tenants/${id}`),
  depts: (tenantId?: number) => getData<Dept[]>('/admin/system/depts', { tenantId }),
  createDept: (data: Record<string, unknown>) => postData<number>('/admin/system/depts', data),
  updateDept: (id: number, data: Record<string, unknown>) => putData<string>(`/admin/system/depts/${id}`, data),
  deleteDept: (id: number) => deleteData<string>(`/admin/system/depts/${id}`),
  roles: () => getData<Role[]>('/admin/system/roles'),
  createRole: (data: Record<string, unknown>) => postData<number>('/admin/system/roles', data),
  updateRole: (id: number, data: Record<string, unknown>) => putData<string>(`/admin/system/roles/${id}`, data),
  deleteRole: (id: number) => deleteData<string>(`/admin/system/roles/${id}`),
  roleMenuIds: (id: number) => getData<number[]>(`/admin/system/roles/${id}/menu-ids`),
  updateRoleMenus: (id: number, menuIds: number[]) => putData<string>(`/admin/system/roles/${id}/menus`, { menuIds }),
  menus: () => getData<MenuItem[]>('/admin/system/menus'),
  createMenu: (data: Record<string, unknown>) => postData<number>('/admin/system/menus', data),
  updateMenu: (id: number, data: Record<string, unknown>) => putData<string>(`/admin/system/menus/${id}`, data),
  deleteMenu: (id: number) => deleteData<string>(`/admin/system/menus/${id}`),
  dictTypes: () => getData<DictType[]>('/admin/system/dict-types'),
  createDictType: (data: Record<string, unknown>) => postData<number>('/admin/system/dict-types', data),
  updateDictType: (id: number, data: Record<string, unknown>) => putData<string>(`/admin/system/dict-types/${id}`, data),
  deleteDictType: (id: number) => deleteData<string>(`/admin/system/dict-types/${id}`),
  dictItems: (dictCode?: string) => getData<DictItem[]>('/admin/system/dict-items', { dictCode }),
  createDictItem: (data: Record<string, unknown>) => postData<number>('/admin/system/dict-items', data),
  updateDictItem: (id: number, data: Record<string, unknown>) => putData<string>(`/admin/system/dict-items/${id}`, data),
  deleteDictItem: (id: number) => deleteData<string>(`/admin/system/dict-items/${id}`),
  configs: () => getData<ConfigItem[]>('/admin/system/configs'),
  createConfig: (data: Record<string, unknown>) => postData<number>('/admin/system/configs', data),
  updateConfig: (id: number, data: Record<string, unknown>) => putData<string>(`/admin/system/configs/${id}`, data),
  deleteConfig: (id: number) => deleteData<string>(`/admin/system/configs/${id}`),
  createUser: (data: Record<string, unknown>) => postData<number>('/admin/system/users', data),
  updateUser: (id: number, data: Record<string, unknown>) => putData<string>(`/admin/system/users/${id}`, data),
  updateUserStatus: (id: number, status: string) => putData<string>(`/admin/system/users/${id}/status`, { status }),
  resetPassword: (id: number, password: string) => putData<string>(`/admin/system/users/${id}/password`, { password }),
  deleteUser: (id: number) => deleteData<string>(`/admin/system/users/${id}`)
}
