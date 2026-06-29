<template>
  <section v-if="!authed" class="login-page">
    <div class="login-panel">
      <div class="brand large">
        <div class="brand-mark">GP</div>
        <div>
          <div class="brand-name">Admin Console</div>
          <div class="brand-subtitle">admin-service</div>
        </div>
      </div>
      <el-form class="login-form" @submit.prevent>
        <el-form-item>
          <el-input v-model="loginForm.username" size="large" placeholder="用户名" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="loginForm.password" size="large" placeholder="密码" show-password @keyup.enter="login" />
        </el-form-item>
        <el-button type="primary" size="large" class="login-button" :loading="loginLoading" @click="login">
          登录
        </el-button>
      </el-form>
    </div>
  </section>

  <el-container v-else class="shell">
    <el-header class="app-header">
      <div class="header-left">
        <div class="brand">
          <div class="brand-mark">GP</div>
          <div class="brand-name">GP Framework</div>
        </div>
        <nav class="top-nav" aria-label="主导航">
          <button
            v-for="item in quickNavItems"
            :key="item.view"
            type="button"
            class="top-nav-item"
            :class="{ active: activeView === item.view }"
            @click="selectView(item.view)"
          >
            {{ item.title }}
          </button>
        </nav>
      </div>
      <div class="header-actions">
        <el-input v-model="traceKeyword" clearable placeholder="Trace ID" class="header-trace" @keyup.enter="searchTrace">
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-popover placement="bottom-end" trigger="click" width="320">
          <template #reference>
            <el-badge :value="alertBadge" :hidden="alertCount === 0" class="header-badge">
              <el-button :icon="Bell" circle @click="loadDashboard" />
            </el-badge>
          </template>
          <div class="alert-panel">
            <div class="alert-panel-head">
              <span>运行告警</span>
              <el-button :icon="Refresh" circle size="small" @click="loadDashboard" />
            </div>
            <button
              v-for="item in alertItems"
              :key="item.title"
              type="button"
              class="alert-item"
              @click="selectView(item.view)"
            >
              <span>
                <strong>{{ item.title }}</strong>
                <small>{{ item.desc }}</small>
              </span>
              <el-tag :type="item.type" size="small">{{ item.count }}</el-tag>
            </button>
            <el-empty v-if="alertItems.length === 0" description="暂无告警" :image-size="72" />
          </div>
        </el-popover>
        <el-tooltip content="刷新">
          <el-button :icon="Refresh" circle @click="refreshCurrent" />
        </el-tooltip>
        <el-tooltip content="修改密码">
          <el-button :icon="Lock" circle @click="openChangePassword" />
        </el-tooltip>
        <el-tooltip content="退出">
          <el-button :icon="SwitchButton" circle @click="logout" />
        </el-tooltip>
        <div class="user-chip">
          <span class="user-avatar">{{ userInitial }}</span>
          <span>{{ currentUser?.username }}</span>
          <el-icon><ArrowDown /></el-icon>
        </div>
      </div>
    </el-header>

    <div class="mobile-view-switch">
      <el-select :model-value="activeView" size="large" @change="selectView">
        <el-option v-for="item in mobileNavOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
    </div>

    <el-container class="shell-body">
      <el-aside width="250px" class="sidebar">
        <div class="sidebar-section">控制台</div>
        <el-menu :default-active="activeView" class="nav" @select="selectView">
          <template v-for="item in navMenus" :key="item.index">
            <el-sub-menu v-if="item.children.length" :index="item.index">
              <template #title>
                <el-icon><component :is="item.icon" /></el-icon>
                <span>{{ item.title }}</span>
              </template>
              <el-menu-item v-for="child in item.children" :key="child.index" :index="child.index">
                <el-icon><component :is="child.icon" /></el-icon>
                <span>{{ child.title }}</span>
              </el-menu-item>
            </el-sub-menu>
            <el-menu-item v-else :index="item.index">
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
            </el-menu-item>
          </template>
        </el-menu>
        <div class="collapse-button">
          <el-button :icon="ArrowLeft" round>收起侧边栏</el-button>
        </div>
      </el-aside>

      <el-container>
        <el-header v-if="activeView === 'dashboard'" class="topbar">
          <div>
            <div class="page-title">{{ greetingTitle }}</div>
            <div class="page-meta">{{ viewTitle }} · {{ currentUser?.roles?.join(', ') || 'ADMIN' }}</div>
          </div>
        </el-header>

      <el-main class="content">
        <section v-if="activeView === 'dashboard'" class="view">
          <div class="metrics">
            <div class="metric"><span>MQ 失败</span><strong>{{ dashboard?.mq?.total ?? 0 }}</strong></div>
            <div class="metric"><span>待重试</span><strong>{{ dashboard?.mq?.pending ?? 0 }}</strong></div>
            <div class="metric"><span>本地消息</span><strong>{{ dashboard?.localMessage?.total ?? 0 }}</strong></div>
            <div class="metric"><span>日志总量</span><strong>{{ dashboard?.logs?.total ?? 0 }}</strong></div>
            <div class="metric"><span>通知记录</span><strong>{{ dashboard?.notifications?.records ?? 0 }}</strong></div>
            <div class="metric"><span>Excel 任务</span><strong>{{ dashboard?.excel?.total ?? 0 }}</strong></div>
            <div class="metric"><span>有效文件</span><strong>{{ dashboard?.files?.active ?? 0 }}</strong></div>
            <div class="metric"><span>文件空间</span><strong>{{ formatBytes(dashboard?.files?.totalSize ?? 0) }}</strong></div>
          </div>
          <el-card shadow="never">
            <template #header><div class="section-head"><span>框架模块</span><el-tag size="small" type="info">{{ dashboard?.modules?.length ?? 0 }}</el-tag></div></template>
            <div class="module-grid">
              <div v-for="item in dashboard?.modules ?? []" :key="item.name" class="module-item">
                <span>{{ item.name }}</span>
                <el-tag :type="item.status === 'UP' ? 'success' : 'danger'" size="small">{{ item.status }}</el-tag>
              </div>
            </div>
          </el-card>
        </section>

        <section v-if="activeView === 'system-tenants'" class="view">
          <el-card shadow="never">
            <template #header>
              <div class="section-head">
                <span>租户管理</span>
                <div class="actions">
                  <el-tag size="small">{{ tenants.length }}</el-tag>
                  <el-button v-if="can('system:tenant:create')" :icon="Plus" circle @click="openCreateTenant" />
                </div>
              </div>
            </template>
            <el-table :data="tenants" height="520" stripe>
              <el-table-column prop="id" label="ID" width="86" />
              <el-table-column prop="tenantCode" label="租户编码" min-width="180" />
              <el-table-column prop="tenantName" label="租户名称" min-width="180" />
              <el-table-column prop="status" label="状态" width="110">
                <template #default="{ row }"><el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag></template>
              </el-table-column>
              <el-table-column prop="createTime" label="创建时间" min-width="170" />
              <el-table-column label="操作" width="116" fixed="right">
                <template #default="{ row }">
                  <el-button v-if="can('system:tenant:update')" :icon="Edit" circle size="small" @click="openEditTenant(row)" />
                  <el-button v-if="can('system:tenant:delete')" :icon="Delete" circle size="small" @click="deleteTenant(row)" />
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </section>

        <section v-if="activeView === 'system-depts'" class="view">
          <el-card shadow="never">
            <template #header>
              <div class="section-head">
                <span>部门管理</span>
                <div class="actions">
                  <el-select v-model="deptQuery.tenantId" class="filter" @change="loadDepts">
                    <el-option v-for="tenant in tenants" :key="tenant.id" :label="tenant.tenantName" :value="tenant.id" />
                  </el-select>
                  <el-tag size="small">{{ flattenDepts(depts).length }}</el-tag>
                  <el-button v-if="can('system:dept:create')" :icon="Plus" circle @click="openCreateDept()" />
                </div>
              </div>
            </template>
            <el-table :data="depts" row-key="id" height="520" stripe default-expand-all>
              <el-table-column prop="deptName" label="部门名称" min-width="180" />
              <el-table-column prop="tenantId" label="租户" width="90" />
              <el-table-column prop="status" label="状态" width="110">
                <template #default="{ row }"><el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag></template>
              </el-table-column>
              <el-table-column prop="sortOrder" label="排序" width="90" />
              <el-table-column prop="createTime" label="创建时间" min-width="170" />
              <el-table-column label="操作" width="148" fixed="right">
                <template #default="{ row }">
                  <el-button v-if="can('system:dept:create')" :icon="Plus" circle size="small" @click="openCreateDept(row)" />
                  <el-button v-if="can('system:dept:update')" :icon="Edit" circle size="small" @click="openEditDept(row)" />
                  <el-button v-if="can('system:dept:delete')" :icon="Delete" circle size="small" @click="deleteDept(row)" />
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </section>

        <section v-if="activeView === 'system-users'" class="view">
          <el-card shadow="never">
            <template #header>
              <div class="section-head">
                <span>用户管理</span>
                <div class="actions">
                  <el-input v-model="userQuery.keyword" clearable placeholder="用户名/昵称/手机号" class="filter wide" />
                  <el-select v-model="userQuery.status" clearable placeholder="状态" class="filter">
                    <el-option label="启用" value="ENABLED" />
                    <el-option label="禁用" value="DISABLED" />
                  </el-select>
                  <el-button :icon="Search" circle type="primary" @click="loadUsers" />
                  <el-button v-if="can('system:user:create')" :icon="Plus" circle @click="openCreateUser" />
                </div>
              </div>
            </template>
            <el-table :data="users.records" height="500" stripe>
              <el-table-column prop="id" label="ID" width="76" />
              <el-table-column prop="username" label="用户名" min-width="120" />
              <el-table-column prop="nickname" label="昵称" min-width="120" />
              <el-table-column prop="mobile" label="手机号" min-width="130" />
              <el-table-column prop="roles" label="角色" min-width="160">
                <template #default="{ row }">{{ row.roles?.join(', ') }}</template>
              </el-table-column>
              <el-table-column prop="lastLoginTime" label="最后登录" min-width="170" />
              <el-table-column label="登录安全" width="132">
                <template #default="{ row }">
                  <el-tag v-if="row.loginLocked" type="danger" size="small">
                    锁定 {{ row.loginLockTtlMinutes || 0 }}m
                  </el-tag>
                  <el-tag v-else-if="row.loginFailCount" type="warning" size="small">
                    失败 {{ row.loginFailCount }}
                  </el-tag>
                  <el-tag v-else type="success" size="small">正常</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.status === 'ENABLED' ? 'success' : 'danger'" size="small">{{ row.status }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="232" fixed="right">
                <template #default="{ row }">
                  <el-button v-if="can('system:user:update')" :icon="Edit" circle size="small" @click="openEditUser(row)" />
                  <el-button v-if="can('system:user:reset-password')" :icon="RefreshRight" circle size="small" @click="resetUserPassword(row.id)" />
                  <el-button v-if="can('system:user:unlock') && row.loginLocked" :icon="Unlock" circle size="small" @click="unlockUser(row)" />
                  <el-button v-if="can('system:user:update-status')" :icon="Switch" circle size="small" @click="toggleUser(row)" />
                  <el-button v-if="can('system:user:delete')" :icon="Delete" circle size="small" @click="deleteUser(row)" />
                </template>
              </el-table-column>
            </el-table>
            <el-pagination v-model:current-page="userQuery.pageNum" v-model:page-size="userQuery.pageSize" class="pager" layout="total, sizes, prev, pager, next" :total="users.total" @change="loadUsers" />
          </el-card>
        </section>

        <section v-if="activeView === 'system-roles'" class="view">
          <el-card shadow="never">
            <template #header>
              <div class="section-head">
                <span>角色管理</span>
                <div class="actions">
                  <el-tag size="small">{{ roles.length }}</el-tag>
                  <el-button v-if="can('system:role:create')" :icon="Plus" circle @click="openCreateRole" />
                </div>
              </div>
            </template>
            <el-table :data="roles" height="520" stripe>
              <el-table-column prop="id" label="ID" width="80" />
              <el-table-column prop="roleCode" label="角色编码" min-width="180" />
              <el-table-column prop="roleName" label="角色名称" min-width="180" />
              <el-table-column prop="status" label="状态" width="120" />
              <el-table-column prop="sortOrder" label="排序" width="100" />
              <el-table-column label="操作" width="148" fixed="right">
                <template #default="{ row }">
                  <el-button v-if="can('system:role:update')" :icon="Edit" circle size="small" @click="openEditRole(row)" />
                  <el-button v-if="can('system:role:authorize')" :icon="Setting" circle size="small" @click="openRoleAuth(row)" />
                  <el-button v-if="can('system:role:delete')" :icon="Delete" circle size="small" @click="deleteRole(row)" />
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </section>

        <section v-if="activeView === 'system-menus'" class="view">
          <el-card shadow="never">
            <template #header>
              <div class="section-head">
                <span>菜单管理</span>
                <div class="actions">
                  <el-tag size="small">{{ menuCount }}</el-tag>
                  <el-button v-if="can('system:menu:create')" :icon="Plus" circle @click="openCreateMenu()" />
                </div>
              </div>
            </template>
            <el-table :data="menus" row-key="id" height="520" stripe default-expand-all>
              <el-table-column prop="menuName" label="菜单" min-width="190" />
              <el-table-column prop="routePath" label="路由" min-width="180" />
              <el-table-column prop="permission" label="权限标识" min-width="190" />
              <el-table-column prop="menuType" label="类型" width="110" />
              <el-table-column label="可见" width="90">
                <template #default="{ row }"><el-tag :type="row.visible === false ? 'info' : 'success'" size="small">{{ row.visible === false ? '隐藏' : '显示' }}</el-tag></template>
              </el-table-column>
              <el-table-column prop="sortOrder" label="排序" width="90" />
              <el-table-column label="操作" width="148" fixed="right">
                <template #default="{ row }">
                  <el-button v-if="can('system:menu:create')" :icon="Plus" circle size="small" @click="openCreateMenu(row)" />
                  <el-button v-if="can('system:menu:update')" :icon="Edit" circle size="small" @click="openEditMenu(row)" />
                  <el-button v-if="can('system:menu:delete')" :icon="Delete" circle size="small" @click="deleteMenu(row)" />
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </section>

        <section v-if="activeView === 'system-dicts'" class="view two-columns">
          <el-card shadow="never">
            <template #header>
              <div class="section-head">
                <span>字典类型</span>
                <div class="actions">
                  <el-tag size="small">{{ dictTypes.length }}</el-tag>
                  <el-button v-if="can('system:dict:create')" :icon="Plus" circle @click="openCreateDictType" />
                </div>
              </div>
            </template>
            <el-table :data="dictTypes" height="520" stripe @row-click="selectDict">
              <el-table-column prop="dictCode" label="编码" min-width="150" />
              <el-table-column prop="dictName" label="名称" min-width="150" />
              <el-table-column prop="status" label="状态" width="100" />
              <el-table-column label="操作" width="108" fixed="right">
                <template #default="{ row }">
                  <el-button v-if="can('system:dict:update')" :icon="Edit" circle size="small" @click.stop="openEditDictType(row)" />
                  <el-button v-if="can('system:dict:delete')" :icon="Delete" circle size="small" @click.stop="deleteDictType(row)" />
                </template>
              </el-table-column>
            </el-table>
          </el-card>
          <el-card shadow="never">
            <template #header>
              <div class="section-head">
                <span>队列状态</span>
                <el-tag size="small" type="info">{{ mqQueues.length }}</el-tag>
              </div>
            </template>
            <el-table :data="mqQueues" height="260" stripe>
              <el-table-column prop="queueName" label="队列" min-width="220" show-overflow-tooltip />
              <el-table-column prop="messageCount" label="消息数" width="110" />
              <el-table-column prop="consumerCount" label="消费者" width="110" />
              <el-table-column prop="state" label="状态" width="130">
                <template #default="{ row }">
                  <el-tag :type="row.state === 'RUNNING' ? 'success' : 'info'" size="small">{{ row.state }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
          <el-card shadow="never">
            <template #header>
              <div class="section-head">
                <span>字典项</span>
                <div class="actions">
                  <el-tag size="small">{{ dictItems.length }}</el-tag>
                  <el-button v-if="can('system:dict:create')" :icon="Plus" circle @click="openCreateDictItem" />
                </div>
              </div>
            </template>
            <el-table :data="dictItems" height="520" stripe>
              <el-table-column prop="itemLabel" label="标签" min-width="140" />
              <el-table-column prop="itemValue" label="值" min-width="140" />
              <el-table-column prop="sortOrder" label="排序" width="90" />
              <el-table-column prop="status" label="状态" width="100" />
              <el-table-column label="操作" width="108" fixed="right">
                <template #default="{ row }">
                  <el-button v-if="can('system:dict:update')" :icon="Edit" circle size="small" @click="openEditDictItem(row)" />
                  <el-button v-if="can('system:dict:delete')" :icon="Delete" circle size="small" @click="deleteDictItem(row)" />
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </section>

        <section v-if="activeView === 'system-configs'" class="view">
          <el-card shadow="never">
            <template #header>
              <div class="section-head">
                <span>参数配置</span>
                <div class="actions">
                  <el-tag size="small">{{ configs.length }}</el-tag>
                  <el-button v-if="can('system:config:create')" :icon="Plus" circle @click="openCreateConfig" />
                </div>
              </div>
            </template>
            <el-table :data="configs" height="520" stripe>
              <el-table-column prop="configKey" label="Key" min-width="220" />
              <el-table-column prop="configName" label="名称" min-width="180" />
              <el-table-column prop="configValue" label="值" min-width="180" show-overflow-tooltip />
              <el-table-column label="敏感" width="86">
                <template #default="{ row }"><el-tag :type="row.sensitive ? 'warning' : 'info'" size="small">{{ row.sensitive ? '是' : '否' }}</el-tag></template>
              </el-table-column>
              <el-table-column prop="remark" label="备注" min-width="240" show-overflow-tooltip />
              <el-table-column label="操作" width="108" fixed="right">
                <template #default="{ row }">
                  <el-button v-if="can('system:config:update')" :icon="Edit" circle size="small" @click="openEditConfig(row)" />
                  <el-button v-if="can('system:config:delete')" :icon="Delete" circle size="small" @click="deleteConfig(row)" />
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </section>

        <section v-if="activeView === 'mq'" class="view">
          <div class="metrics compact">
            <div class="metric"><span>PENDING</span><strong>{{ mqStats?.pendingCount ?? 0 }}</strong></div>
            <div class="metric"><span>RETRYING</span><strong>{{ mqStats?.retryingCount ?? 0 }}</strong></div>
            <div class="metric"><span>SUCCESS</span><strong>{{ mqStats?.successCount ?? 0 }}</strong></div>
            <div class="metric"><span>EXHAUSTED</span><strong>{{ mqStats?.exhaustedCount ?? 0 }}</strong></div>
          </div>
          <el-card shadow="never">
            <template #header>
              <div class="section-head">
                <span>运行配置</span>
                <el-tag size="small" :type="mqStats?.runtime?.enabled ? 'success' : 'info'">
                  {{ mqStats?.runtime?.enabled ? '已启用' : '未启用' }}
                </el-tag>
              </div>
            </template>
            <div class="runtime-grid">
              <div class="runtime-item">
                <span>当前 Provider</span>
                <strong>{{ mqStats?.runtime?.provider || '-' }}</strong>
              </div>
              <div class="runtime-item">
                <span>死信队列</span>
                <strong>{{ mqStats?.runtime?.deadLetterQueue || '-' }}</strong>
              </div>
              <div class="runtime-item">
                <span>重发能力</span>
                <strong>{{ mqStats?.runtime?.retryAvailable ? '可用' : '未接入' }}</strong>
              </div>
              <div class="runtime-item">
                <span>最大重试</span>
                <strong>{{ mqStats?.runtime?.maxRetry ?? 0 }}</strong>
              </div>
              <div class="runtime-item">
                <span>重试间隔</span>
                <strong>{{ mqStats?.runtime?.retryFixedDelay ?? 0 }} ms</strong>
              </div>
              <div class="runtime-item table-name">
                <span>失败消息表</span>
                <strong>{{ mqStats?.runtime?.failedMessageTableName || '-' }}</strong>
              </div>
            </div>
            <div class="provider-strip">
              <el-tag
                v-for="provider in mqStats?.runtime?.providers ?? []"
                :key="provider.provider"
                :type="providerTagType(provider)"
                effect="plain"
              >
                {{ provider.provider }} · {{ provider.active ? '当前' : provider.available ? '可用' : '未接入' }}
              </el-tag>
            </div>
          </el-card>
          <el-card shadow="never">
            <template #header>
              <div class="section-head">
                <span>失败消息</span>
                <div class="actions">
                  <el-select v-model="mqQuery.status" clearable placeholder="状态" class="filter">
                    <el-option label="PENDING" value="PENDING" />
                    <el-option label="RETRYING" value="RETRYING" />
                    <el-option label="EXHAUSTED" value="EXHAUSTED" />
                    <el-option label="MANUAL" value="MANUAL" />
                  </el-select>
                  <el-input v-model="mqQuery.traceId" clearable placeholder="Trace ID" class="filter" />
                  <el-button :icon="Search" circle type="primary" @click="loadMq" />
                  <el-button v-if="can('mq:retry')" :icon="RefreshRight" circle :disabled="!mqStats?.runtime?.retryAvailable || selectedMqMessageIds.length === 0" @click="batchRetryMq" />
                  <el-button v-if="can('mq:retry')" :icon="Delete" circle @click="cleanMq" />
                </div>
              </div>
            </template>
            <el-table :data="mqMessages.records" height="420" stripe @selection-change="handleMqSelectionChange">
              <el-table-column type="selection" width="44" />
              <el-table-column prop="id" label="ID" width="86" />
              <el-table-column prop="status" label="状态" width="118">
                <template #default="{ row }"><el-tag :type="mqStatusType(row.status)" size="small">{{ row.status }}</el-tag></template>
              </el-table-column>
              <el-table-column prop="messageType" label="类型" min-width="140" show-overflow-tooltip />
              <el-table-column prop="businessKey" label="业务键" min-width="150" show-overflow-tooltip />
              <el-table-column prop="traceId" label="Trace ID" min-width="190" show-overflow-tooltip />
              <el-table-column prop="retryCount" label="重试" width="82" />
              <el-table-column prop="errorMessage" label="错误" min-width="220" show-overflow-tooltip />
              <el-table-column label="操作" width="220" fixed="right">
                <template #default="{ row }">
                  <el-button v-if="can('mq:retry')" :icon="RefreshRight" circle size="small" :disabled="!mqStats?.runtime?.retryAvailable" @click="retryMq(row.id)" />
                  <el-button v-if="can('mq:retry')" :icon="Check" circle size="small" @click="manualSuccessMq(row)" />
                  <el-button v-if="can('mq:retry')" :icon="Close" circle size="small" @click="manualFailureMq(row)" />
                  <el-button v-if="can('mq:retry')" :icon="Delete" circle size="small" @click="deleteMq(row)" />
                  <el-button :icon="View" circle size="small" @click="openDetail(row)" />
                </template>
              </el-table-column>
            </el-table>
            <el-pagination v-model:current-page="mqQuery.pageNum" v-model:page-size="mqQuery.pageSize" class="pager" layout="total, sizes, prev, pager, next" :total="mqMessages.total" @change="loadMq" />
          </el-card>
        </section>

        <section v-if="activeView === 'local'" class="view">
          <el-card shadow="never">
            <template #header>
              <div class="section-head">
                <span>本地消息表</span>
                <div class="actions">
                  <el-select v-model="localQuery.status" clearable placeholder="状态" class="filter">
                    <el-option label="PENDING" value="PENDING" />
                    <el-option label="PROCESSING" value="PROCESSING" />
                    <el-option label="SUCCESS" value="SUCCESS" />
                    <el-option label="FAILED" value="FAILED" />
                  </el-select>
                  <el-input v-model="localQuery.topic" clearable placeholder="Topic" class="filter" />
                  <el-button :icon="Search" circle type="primary" @click="loadLocal" />
                  <el-button v-if="can('local-message:retry')" :icon="RefreshRight" circle @click="retryLocal" />
                </div>
              </div>
            </template>
            <el-table :data="localMessages.records" height="500" stripe>
              <el-table-column prop="id" label="ID" width="86" />
              <el-table-column prop="status" label="状态" width="126">
                <template #default="{ row }"><el-tag :type="localStatusType(row.status)" size="small">{{ row.status }}</el-tag></template>
              </el-table-column>
              <el-table-column prop="topic" label="Topic" min-width="150" show-overflow-tooltip />
              <el-table-column prop="businessKey" label="业务键" min-width="150" show-overflow-tooltip />
              <el-table-column prop="traceId" label="Trace ID" min-width="190" show-overflow-tooltip />
              <el-table-column prop="retryCount" label="重试" width="82" />
              <el-table-column prop="errorMessage" label="错误" min-width="220" show-overflow-tooltip />
              <el-table-column label="操作" width="166" fixed="right">
                <template #default="{ row }">
                  <el-button v-if="can('local-message:retry')" :icon="Check" circle size="small" @click="markLocalSuccess(row)" />
                  <el-button v-if="can('local-message:retry')" :icon="Close" circle size="small" @click="markLocalFailure(row)" />
                  <el-button v-if="can('local-message:retry')" :icon="Delete" circle size="small" @click="deleteLocal(row)" />
                  <el-button :icon="View" circle size="small" @click="openDetail(row)" />
                </template>
              </el-table-column>
            </el-table>
            <el-pagination v-model:current-page="localQuery.pageNum" v-model:page-size="localQuery.pageSize" class="pager" layout="total, sizes, prev, pager, next" :total="localMessages.total" @change="loadLocal" />
          </el-card>
        </section>

        <section v-if="activeView === 'notify'" class="view">
          <div class="metrics compact">
            <div class="metric"><span>启用模板</span><strong>{{ notifyStats?.enabledTemplates ?? 0 }}</strong></div>
            <div class="metric"><span>禁用模板</span><strong>{{ notifyStats?.disabledTemplates ?? 0 }}</strong></div>
            <div class="metric"><span>发送成功</span><strong>{{ notifyStats?.successRecords ?? 0 }}</strong></div>
            <div class="metric"><span>发送失败</span><strong>{{ notifyStats?.failedRecords ?? 0 }}</strong></div>
          </div>
          <el-card shadow="never">
            <template #header>
              <div class="section-head">
                <span>通知模板</span>
                <div class="actions">
                  <el-input v-model="notifyQuery.keyword" clearable placeholder="模板/标题" class="filter" />
                  <el-select v-model="notifyQuery.channel" clearable placeholder="通道" class="filter">
                    <el-option label="LOG" value="LOG" />
                    <el-option label="WEBHOOK" value="WEBHOOK" />
                    <el-option label="SMS" value="SMS" />
                    <el-option label="EMAIL" value="EMAIL" />
                  </el-select>
                  <el-select v-model="notifyQuery.status" clearable placeholder="状态" class="filter">
                    <el-option label="启用" value="ENABLED" />
                    <el-option label="禁用" value="DISABLED" />
                  </el-select>
                  <el-button :icon="Search" circle type="primary" @click="loadNotify" />
                  <el-button v-if="can('notify:template:create')" :icon="Plus" circle @click="openCreateNotify" />
                </div>
              </div>
            </template>
            <el-table :data="notifyTemplates.records" height="320" stripe>
              <el-table-column prop="templateCode" label="编码" min-width="150" show-overflow-tooltip />
              <el-table-column prop="templateName" label="名称" min-width="150" show-overflow-tooltip />
              <el-table-column prop="channel" label="通道" width="110" />
              <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
              <el-table-column prop="status" label="状态" width="100">
                <template #default="{ row }"><el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag></template>
              </el-table-column>
              <el-table-column label="操作" width="176" fixed="right">
                <template #default="{ row }">
                  <el-button v-if="can('notify:template:update')" :icon="Edit" circle size="small" @click="openEditNotify(row)" />
                  <el-button v-if="can('notify:send-test')" :icon="Bell" circle size="small" @click="sendTestNotify(row)" />
                  <el-button v-if="can('notify:template:delete')" :icon="Delete" circle size="small" @click="deleteNotify(row)" />
                  <el-button :icon="View" circle size="small" @click="openDetail(row)" />
                </template>
              </el-table-column>
            </el-table>
            <el-pagination v-model:current-page="notifyQuery.pageNum" v-model:page-size="notifyQuery.pageSize" class="pager" layout="total, sizes, prev, pager, next" :total="notifyTemplates.total" @change="loadNotify" />
          </el-card>

          <el-card shadow="never">
            <template #header>
              <div class="section-head">
                <span>发送记录</span>
                <div class="actions">
                  <el-select v-model="notifyRecordQuery.channel" clearable placeholder="通道" class="filter">
                    <el-option label="LOG" value="LOG" />
                    <el-option label="WEBHOOK" value="WEBHOOK" />
                    <el-option label="SMS" value="SMS" />
                    <el-option label="EMAIL" value="EMAIL" />
                  </el-select>
                  <el-select v-model="notifyRecordQuery.success" clearable placeholder="结果" class="filter">
                    <el-option label="成功" :value="true" />
                    <el-option label="失败" :value="false" />
                  </el-select>
                  <el-button :icon="Search" circle type="primary" @click="loadNotify" />
                </div>
              </div>
            </template>
            <el-table :data="notifyRecords.records" height="260" stripe>
              <el-table-column prop="id" label="ID" width="86" />
              <el-table-column prop="templateCode" label="模板" min-width="140" show-overflow-tooltip />
              <el-table-column prop="channel" label="通道" width="110" />
              <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
              <el-table-column label="结果" width="90">
                <template #default="{ row }"><el-tag :type="row.success ? 'success' : 'danger'" size="small">{{ row.success ? '成功' : '失败' }}</el-tag></template>
              </el-table-column>
              <el-table-column prop="resultMessage" label="说明" min-width="180" show-overflow-tooltip />
              <el-table-column prop="traceId" label="Trace ID" min-width="180" show-overflow-tooltip />
              <el-table-column label="操作" width="80" fixed="right">
                <template #default="{ row }"><el-button :icon="View" circle size="small" @click="openDetail(row)" /></template>
              </el-table-column>
            </el-table>
            <el-pagination v-model:current-page="notifyRecordQuery.pageNum" v-model:page-size="notifyRecordQuery.pageSize" class="pager" layout="total, sizes, prev, pager, next" :total="notifyRecords.total" @change="loadNotify" />
          </el-card>
        </section>

        <section v-if="activeView === 'excel'" class="view">
          <div class="metrics compact">
            <div class="metric"><span>任务总数</span><strong>{{ excelStats?.total ?? 0 }}</strong></div>
            <div class="metric"><span>成功</span><strong>{{ excelStats?.success ?? 0 }}</strong></div>
            <div class="metric"><span>失败</span><strong>{{ excelStats?.failed ?? 0 }}</strong></div>
            <div class="metric"><span>导入/导出</span><strong>{{ excelStats?.import ?? 0 }}/{{ excelStats?.export ?? 0 }}</strong></div>
          </div>
          <el-card shadow="never">
            <template #header>
              <div class="section-head">
                <span>任务列表</span>
                <div class="actions">
                  <el-select v-model="excelQuery.taskType" clearable placeholder="类型" class="filter">
                    <el-option label="IMPORT" value="IMPORT" />
                    <el-option label="EXPORT" value="EXPORT" />
                  </el-select>
                  <el-select v-model="excelQuery.status" clearable placeholder="状态" class="filter">
                    <el-option label="SUCCESS" value="SUCCESS" />
                    <el-option label="FAILED" value="FAILED" />
                    <el-option label="PROCESSING" value="PROCESSING" />
                  </el-select>
                  <el-button :icon="Search" circle type="primary" @click="loadExcel" />
                  <el-button v-if="can('excel:task:create')" :icon="Files" circle @click="createExportTask" />
                  <el-button v-if="can('excel:task:create')" :icon="Close" circle @click="createImportFailureTask" />
                </div>
              </div>
            </template>
            <el-table :data="excelTasks.records" height="500" stripe>
              <el-table-column prop="id" label="ID" width="86" />
              <el-table-column prop="taskName" label="任务" min-width="170" show-overflow-tooltip />
              <el-table-column prop="taskType" label="类型" width="110" />
              <el-table-column prop="bizType" label="业务" min-width="120" show-overflow-tooltip />
              <el-table-column prop="status" label="状态" width="110">
                <template #default="{ row }"><el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag></template>
              </el-table-column>
              <el-table-column prop="filename" label="文件" min-width="190" show-overflow-tooltip />
              <el-table-column prop="totalRows" label="总行" width="82" />
              <el-table-column prop="successRows" label="成功" width="82" />
              <el-table-column prop="failureRows" label="失败" width="82" />
              <el-table-column prop="errorMessage" label="错误" min-width="180" show-overflow-tooltip />
              <el-table-column label="操作" width="116" fixed="right">
                <template #default="{ row }">
                  <el-button :icon="Document" circle size="small" @click="loadExcelErrors(row)" />
                  <el-button :icon="View" circle size="small" @click="openDetail(row)" />
                </template>
              </el-table-column>
            </el-table>
            <el-pagination v-model:current-page="excelQuery.pageNum" v-model:page-size="excelQuery.pageSize" class="pager" layout="total, sizes, prev, pager, next" :total="excelTasks.total" @change="loadExcel" />
          </el-card>
        </section>

        <section v-if="activeView === 'files'" class="view">
          <div class="metrics compact">
            <div class="metric"><span>有效文件</span><strong>{{ fileStats?.active ?? 0 }}</strong></div>
            <div class="metric"><span>已删除</span><strong>{{ fileStats?.deleted ?? 0 }}</strong></div>
            <div class="metric"><span>占用空间</span><strong>{{ formatBytes(fileStats?.totalSize ?? 0) }}</strong></div>
          </div>
          <el-card shadow="never">
            <template #header>
              <div class="section-head">
                <span>文件列表</span>
                <div class="actions">
                  <el-input v-model="fileQuery.keyword" clearable placeholder="文件/业务键" class="filter" />
                  <el-input v-model="fileQuery.businessType" clearable placeholder="业务类型" class="filter" />
                  <el-input v-model="fileQuery.contentType" clearable placeholder="Content-Type" class="filter" />
                  <el-button :icon="Search" circle type="primary" @click="loadFiles" />
                  <el-button v-if="can('file:upload')" :icon="Upload" circle @click="chooseFile" />
                  <input ref="fileInputRef" class="file-input" type="file" @change="uploadSelectedFile" />
                </div>
              </div>
            </template>
            <el-table :data="fileRecords.records" height="500" stripe>
              <el-table-column prop="id" label="ID" width="86" />
              <el-table-column prop="originalFilename" label="文件名" min-width="190" show-overflow-tooltip />
              <el-table-column prop="contentType" label="类型" min-width="150" show-overflow-tooltip />
              <el-table-column label="大小" width="110">
                <template #default="{ row }">{{ formatBytes(row.fileSize) }}</template>
              </el-table-column>
              <el-table-column prop="businessType" label="业务类型" min-width="120" show-overflow-tooltip />
              <el-table-column prop="businessKey" label="业务键" min-width="140" show-overflow-tooltip />
              <el-table-column prop="operatorName" label="上传人" width="120" />
              <el-table-column prop="createTime" label="上传时间" min-width="170" />
              <el-table-column label="操作" width="134" fixed="right">
                <template #default="{ row }">
                  <el-button :icon="Download" circle size="small" @click="downloadFile(row)" />
                  <el-button v-if="can('file:delete')" :icon="Delete" circle size="small" @click="deleteFile(row)" />
                  <el-button :icon="View" circle size="small" @click="openDetail(row)" />
                </template>
              </el-table-column>
            </el-table>
            <el-pagination v-model:current-page="fileQuery.pageNum" v-model:page-size="fileQuery.pageSize" class="pager" layout="total, sizes, prev, pager, next" :total="fileRecords.total" @change="loadFiles" />
          </el-card>
        </section>

        <section v-if="activeView === 'trace'" class="view">
          <div class="metrics compact">
            <div class="metric"><span>日志</span><strong>{{ traceDetail?.summary?.logs ?? 0 }}</strong></div>
            <div class="metric"><span>MQ 消息</span><strong>{{ traceDetail?.summary?.mqMessages ?? 0 }}</strong></div>
            <div class="metric"><span>本地消息</span><strong>{{ traceDetail?.summary?.localMessages ?? 0 }}</strong></div>
            <div class="metric"><span>失败节点</span><strong>{{ traceDetail?.summary?.failed ?? 0 }}</strong></div>
          </div>

          <el-card shadow="never">
            <template #header>
              <div class="section-head">
                <span>链路时间线</span>
                <div class="actions">
                  <el-input v-model="logQuery.traceId" clearable placeholder="Trace ID" class="filter wide" />
                  <el-button :icon="Search" circle type="primary" @click="loadTrace" />
                </div>
              </div>
            </template>
            <el-empty v-if="!traceDetail || traceDetail.timeline.length === 0" description="暂无链路数据" />
            <el-timeline v-else class="trace-timeline">
              <el-timeline-item
                v-for="(event, index) in traceDetail.timeline"
                :key="`${event.source}-${index}`"
                :timestamp="event.time"
                placement="top"
              >
                <div class="trace-event">
                  <div class="trace-event-head">
                    <el-tag size="small" :type="traceSourceType(event.source)">{{ event.source }}</el-tag>
                    <strong>{{ event.title || '-' }}</strong>
                    <el-tag size="small" :type="statusType(event.status)">{{ event.status || 'UNKNOWN' }}</el-tag>
                  </div>
                  <div v-if="event.businessKey || event.message" class="trace-event-body">
                    <span v-if="event.businessKey">业务键：{{ event.businessKey }}</span>
                    <span v-if="event.message">{{ event.message }}</span>
                  </div>
                </div>
              </el-timeline-item>
            </el-timeline>
          </el-card>

          <el-card shadow="never">
            <template #header><div class="section-head"><span>链路明细</span><el-tag size="small">{{ traceDetail?.traceId || '-' }}</el-tag></div></template>
            <el-tabs>
              <el-tab-pane label="日志">
                <el-table :data="traceDetail?.logs ?? []" height="360" stripe>
                  <el-table-column prop="id" label="ID" width="86" />
                  <el-table-column prop="logType" label="类型" width="122" />
                  <el-table-column prop="module" label="模块" min-width="120" show-overflow-tooltip />
                  <el-table-column prop="action" label="动作" min-width="160" show-overflow-tooltip />
                  <el-table-column prop="uri" label="URI" min-width="180" show-overflow-tooltip />
                  <el-table-column prop="elapsedMs" label="耗时" width="92" />
                  <el-table-column label="操作" width="80" fixed="right">
                    <template #default="{ row }"><el-button :icon="View" circle size="small" @click="openDetail(row)" /></template>
                  </el-table-column>
                </el-table>
              </el-tab-pane>
              <el-tab-pane label="MQ">
                <el-table :data="traceDetail?.mqMessages ?? []" height="360" stripe>
                  <el-table-column prop="id" label="ID" width="86" />
                  <el-table-column prop="status" label="状态" width="120" />
                  <el-table-column prop="messageType" label="类型" min-width="150" show-overflow-tooltip />
                  <el-table-column prop="queueName" label="队列" min-width="160" show-overflow-tooltip />
                  <el-table-column prop="businessKey" label="业务键" min-width="150" show-overflow-tooltip />
                  <el-table-column prop="errorMessage" label="错误" min-width="220" show-overflow-tooltip />
                  <el-table-column label="操作" width="80" fixed="right">
                    <template #default="{ row }"><el-button :icon="View" circle size="small" @click="openDetail(row)" /></template>
                  </el-table-column>
                </el-table>
              </el-tab-pane>
              <el-tab-pane label="本地消息">
                <el-table :data="traceDetail?.localMessages ?? []" height="360" stripe>
                  <el-table-column prop="id" label="ID" width="86" />
                  <el-table-column prop="status" label="状态" width="126" />
                  <el-table-column prop="topic" label="Topic" min-width="150" show-overflow-tooltip />
                  <el-table-column prop="businessKey" label="业务键" min-width="150" show-overflow-tooltip />
                  <el-table-column prop="retryCount" label="重试" width="82" />
                  <el-table-column prop="errorMessage" label="错误" min-width="220" show-overflow-tooltip />
                  <el-table-column label="操作" width="80" fixed="right">
                    <template #default="{ row }"><el-button :icon="View" circle size="small" @click="openDetail(row)" /></template>
                  </el-table-column>
                </el-table>
              </el-tab-pane>
            </el-tabs>
          </el-card>
        </section>

        <section v-if="activeView === 'logs'" class="view">
          <el-card shadow="never">
            <template #header>
              <div class="section-head">
                <span>日志列表</span>
                <div class="actions">
                  <el-select v-model="logQuery.logType" clearable placeholder="类型" class="filter">
                    <el-option label="OPERATION" value="OPERATION" />
                    <el-option label="API" value="API" />
                    <el-option label="LOGIN" value="LOGIN" />
                    <el-option label="EXCEPTION" value="EXCEPTION" />
                  </el-select>
                  <el-input v-model="logQuery.traceId" clearable placeholder="Trace ID" class="filter" />
                  <el-button :icon="Search" circle type="primary" @click="loadLogs" />
                </div>
              </div>
            </template>
            <el-table :data="logs.records" height="500" stripe>
              <el-table-column prop="id" label="ID" width="86" />
              <el-table-column prop="logType" label="类型" width="122" />
              <el-table-column prop="module" label="模块" min-width="120" show-overflow-tooltip />
              <el-table-column prop="action" label="动作" min-width="160" show-overflow-tooltip />
              <el-table-column prop="uri" label="URI" min-width="180" show-overflow-tooltip />
              <el-table-column prop="elapsedMs" label="耗时" width="92" />
              <el-table-column prop="traceId" label="Trace ID" min-width="190" show-overflow-tooltip />
              <el-table-column label="状态" width="90">
                <template #default="{ row }"><el-tag :type="row.success === false ? 'danger' : 'success'" size="small">{{ row.success === false ? 'FAIL' : 'OK' }}</el-tag></template>
              </el-table-column>
              <el-table-column label="操作" width="80" fixed="right">
                <template #default="{ row }"><el-button :icon="View" circle size="small" @click="openDetail(row)" /></template>
              </el-table-column>
            </el-table>
            <el-pagination v-model:current-page="logQuery.pageNum" v-model:page-size="logQuery.pageSize" class="pager" layout="total, sizes, prev, pager, next" :total="logs.total" @change="loadLogs" />
          </el-card>
        </section>

        <section v-if="activeView === 'login-logs'" class="view">
          <el-card shadow="never">
            <template #header>
              <div class="section-head">
                <span>登录日志</span>
                <div class="actions">
                  <el-input v-model="loginLogQuery.username" clearable placeholder="用户名" class="filter" />
                  <el-select v-model="loginLogQuery.success" clearable placeholder="结果" class="filter">
                    <el-option label="成功" :value="true" />
                    <el-option label="失败" :value="false" />
                  </el-select>
                  <el-button :icon="Search" circle type="primary" @click="loadLoginLogs" />
                </div>
              </div>
            </template>
            <el-table :data="loginLogs.records" height="500" stripe>
              <el-table-column prop="id" label="ID" width="86" />
              <el-table-column prop="username" label="用户名" min-width="140" />
              <el-table-column prop="clientIp" label="IP" min-width="150" />
              <el-table-column label="结果" width="90">
                <template #default="{ row }"><el-tag :type="row.success ? 'success' : 'danger'" size="small">{{ row.success ? '成功' : '失败' }}</el-tag></template>
              </el-table-column>
              <el-table-column prop="message" label="说明" min-width="220" show-overflow-tooltip />
              <el-table-column prop="createTime" label="时间" min-width="170" />
              <el-table-column label="操作" width="80" fixed="right">
                <template #default="{ row }"><el-button :icon="View" circle size="small" @click="openDetail(row)" /></template>
              </el-table-column>
            </el-table>
            <el-pagination v-model:current-page="loginLogQuery.pageNum" v-model:page-size="loginLogQuery.pageSize" class="pager" layout="total, sizes, prev, pager, next" :total="loginLogs.total" @change="loadLoginLogs" />
          </el-card>
        </section>

        <section v-if="activeView === 'sessions'" class="view">
          <el-card shadow="never">
            <template #header>
              <div class="section-head">
                <span>在线会话</span>
                <div class="actions">
                  <el-tag size="small">{{ onlineSessions.length }}</el-tag>
                  <el-button :icon="Refresh" circle @click="loadSessions" />
                </div>
              </div>
            </template>
            <el-table :data="onlineSessions" height="500" stripe>
              <el-table-column prop="username" label="用户名" min-width="140" />
              <el-table-column prop="userId" label="用户ID" width="100" />
              <el-table-column prop="tenantId" label="租户" width="110" />
              <el-table-column prop="deviceId" label="设备" min-width="180" show-overflow-tooltip />
              <el-table-column label="登录时间" min-width="180">
                <template #default="{ row }">{{ formatLoginTime(row.loginTime) }}</template>
              </el-table-column>
              <el-table-column label="剩余有效期" width="130">
                <template #default="{ row }">{{ formatTtl(row.ttlSeconds) }}</template>
              </el-table-column>
              <el-table-column label="当前" width="90">
                <template #default="{ row }">
                  <el-tag :type="isCurrentSession(row) ? 'success' : 'info'" size="small">
                    {{ isCurrentSession(row) ? '是' : '否' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="90" fixed="right">
                <template #default="{ row }">
                  <el-tooltip content="强制下线">
                    <el-button
                      v-if="can('session:kick')"
                      :icon="SwitchButton"
                      circle
                      size="small"
                      :disabled="isCurrentSession(row)"
                      @click="kickSession(row)"
                    />
                  </el-tooltip>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </section>

        <section v-if="activeView === 'monitor'" class="view">
          <el-card shadow="never">
            <template #header>
              <div class="section-head">
                <span>服务健康</span>
                <el-tag size="small" :type="health?.status === 'UP' ? 'success' : 'danger'">
                  {{ health?.status || '-' }}
                </el-tag>
              </div>
            </template>
            <div class="health-grid">
              <div v-for="(component, name) in health?.components ?? {}" :key="name" class="health-item">
                <div class="health-title">
                  <span>{{ name }}</span>
                  <el-tag size="small" :type="component.status === 'UP' ? 'success' : 'danger'">
                    {{ component.status }}
                  </el-tag>
                </div>
                <div class="health-detail">{{ formatHealthDetails(component.details) }}</div>
              </div>
            </div>
          </el-card>
          <el-card shadow="never">
            <template #header><div class="section-head"><span>运行时</span><el-button :icon="Refresh" circle @click="loadMonitor" /></div></template>
            <div class="runtime-grid">
              <div v-for="(value, key) in jvm" :key="key" class="runtime-item">
                <span>{{ key }}</span>
                <strong>{{ formatRuntime(value) }}</strong>
              </div>
            </div>
          </el-card>
        </section>
      </el-main>
    </el-container>
    </el-container>
  </el-container>

  <el-dialog v-model="userDialogVisible" :title="userDialogMode === 'create' ? '新增用户' : '编辑用户'" width="520px">
    <el-form label-width="88px">
      <el-form-item label="用户名"><el-input v-model="userForm.username" :disabled="userDialogMode === 'edit'" /></el-form-item>
      <el-form-item v-if="userDialogMode === 'create'" label="密码"><el-input v-model="userForm.password" show-password /></el-form-item>
      <el-form-item label="昵称"><el-input v-model="userForm.nickname" /></el-form-item>
      <el-form-item label="手机号"><el-input v-model="userForm.mobile" /></el-form-item>
      <el-form-item label="邮箱"><el-input v-model="userForm.email" /></el-form-item>
      <el-form-item label="部门">
        <el-select v-model="userForm.deptId" filterable>
          <el-option v-for="dept in flattenDepts(depts)" :key="dept.id" :label="dept.deptName" :value="dept.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="userForm.status">
          <el-option label="启用" value="ENABLED" />
          <el-option label="禁用" value="DISABLED" />
        </el-select>
      </el-form-item>
      <el-form-item label="角色">
        <el-select v-model="userForm.roleIds" multiple>
          <el-option v-for="role in roles" :key="role.id" :label="role.roleName" :value="role.id" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="userDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="saveUser">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="tenantDialogVisible" :title="editingTenantId ? '编辑租户' : '新增租户'" width="460px">
    <el-form label-width="88px">
      <el-form-item label="租户编码"><el-input v-model="tenantForm.tenantCode" /></el-form-item>
      <el-form-item label="租户名称"><el-input v-model="tenantForm.tenantName" /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="tenantForm.status">
          <el-option label="启用" value="ENABLED" />
          <el-option label="禁用" value="DISABLED" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="tenantDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="saveTenant">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="deptDialogVisible" :title="editingDeptId ? '编辑部门' : '新增部门'" width="500px">
    <el-form label-width="88px">
      <el-form-item label="租户">
        <el-select v-model="deptForm.tenantId" filterable>
          <el-option v-for="tenant in tenants" :key="tenant.id" :label="tenant.tenantName" :value="tenant.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="上级部门">
        <el-select v-model="deptForm.parentId" filterable>
          <el-option v-for="dept in deptParentOptions()" :key="dept.id" :label="dept.deptName" :value="dept.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="部门名称"><el-input v-model="deptForm.deptName" /></el-form-item>
      <el-form-item label="排序"><el-input-number v-model="deptForm.sortOrder" :min="0" /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="deptForm.status">
          <el-option label="启用" value="ENABLED" />
          <el-option label="禁用" value="DISABLED" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="deptDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="saveDept">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="roleDialogVisible" :title="currentRole ? '编辑角色' : '新增角色'" width="460px">
    <el-form label-width="88px">
      <el-form-item label="角色编码"><el-input v-model="roleForm.roleCode" /></el-form-item>
      <el-form-item label="角色名称"><el-input v-model="roleForm.roleName" /></el-form-item>
      <el-form-item label="排序"><el-input-number v-model="roleForm.sortOrder" :min="0" /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="roleForm.status">
          <el-option label="启用" value="ENABLED" />
          <el-option label="禁用" value="DISABLED" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="roleDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="saveRole">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="menuDialogVisible" :title="editingMenuId ? '编辑菜单' : '新增菜单'" width="560px">
    <el-form label-width="88px">
      <el-form-item label="上级菜单">
        <el-select v-model="menuForm.parentId" filterable>
          <el-option v-for="item in menuParentOptions()" :key="item.id" :label="item.menuName" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="类型">
        <el-radio-group v-model="menuForm.menuType">
          <el-radio-button label="MENU" />
          <el-radio-button label="BUTTON" />
        </el-radio-group>
      </el-form-item>
      <el-form-item label="名称"><el-input v-model="menuForm.menuName" /></el-form-item>
      <el-form-item label="路由"><el-input v-model="menuForm.routePath" /></el-form-item>
      <el-form-item label="组件"><el-input v-model="menuForm.component" /></el-form-item>
      <el-form-item label="权限"><el-input v-model="menuForm.permission" /></el-form-item>
      <el-form-item label="图标"><el-input v-model="menuForm.icon" /></el-form-item>
      <el-form-item label="排序"><el-input-number v-model="menuForm.sortOrder" :min="0" /></el-form-item>
      <el-form-item label="可见"><el-switch v-model="menuForm.visible" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="menuDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="saveMenu">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="dictTypeDialogVisible" :title="editingDictTypeId ? '编辑字典类型' : '新增字典类型'" width="460px">
    <el-form label-width="88px">
      <el-form-item label="编码"><el-input v-model="dictTypeForm.dictCode" /></el-form-item>
      <el-form-item label="名称"><el-input v-model="dictTypeForm.dictName" /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="dictTypeForm.status">
          <el-option label="启用" value="ENABLED" />
          <el-option label="禁用" value="DISABLED" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dictTypeDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="saveDictType">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="dictItemDialogVisible" :title="editingDictItemId ? '编辑字典项' : '新增字典项'" width="460px">
    <el-form label-width="88px">
      <el-form-item label="字典编码"><el-input v-model="dictItemForm.dictCode" /></el-form-item>
      <el-form-item label="标签"><el-input v-model="dictItemForm.itemLabel" /></el-form-item>
      <el-form-item label="值"><el-input v-model="dictItemForm.itemValue" /></el-form-item>
      <el-form-item label="排序"><el-input-number v-model="dictItemForm.sortOrder" :min="0" /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="dictItemForm.status">
          <el-option label="启用" value="ENABLED" />
          <el-option label="禁用" value="DISABLED" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dictItemDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="saveDictItem">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="configDialogVisible" :title="editingConfigId ? '编辑参数' : '新增参数'" width="520px">
    <el-form label-width="88px">
      <el-form-item label="Key"><el-input v-model="configForm.configKey" /></el-form-item>
      <el-form-item label="名称"><el-input v-model="configForm.configName" /></el-form-item>
      <el-form-item label="值"><el-input v-model="configForm.configValue" type="textarea" :rows="3" /></el-form-item>
      <el-form-item label="敏感"><el-switch v-model="configForm.sensitive" /></el-form-item>
      <el-form-item label="备注"><el-input v-model="configForm.remark" type="textarea" :rows="2" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="configDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="saveConfig">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="roleAuthVisible" title="角色授权" width="520px">
    <div class="auth-title">{{ currentRole?.roleName }} / {{ currentRole?.roleCode }}</div>
    <el-tree
      ref="roleMenuTreeRef"
      :data="menus"
      node-key="id"
      show-checkbox
      default-expand-all
      :props="{ label: 'menuName', children: 'children' }"
      :default-checked-keys="checkedMenuIds"
    />
    <template #footer>
      <el-button @click="roleAuthVisible = false">取消</el-button>
      <el-button type="primary" @click="saveRoleAuth">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="notifyDialogVisible" :title="editingNotifyId ? '编辑通知模板' : '新增通知模板'" width="560px">
    <el-form label-width="88px">
      <el-form-item label="模板编码"><el-input v-model="notifyForm.templateCode" /></el-form-item>
      <el-form-item label="模板名称"><el-input v-model="notifyForm.templateName" /></el-form-item>
      <el-form-item label="通道">
        <el-select v-model="notifyForm.channel">
          <el-option label="LOG" value="LOG" />
          <el-option label="WEBHOOK" value="WEBHOOK" />
          <el-option label="SMS" value="SMS" />
          <el-option label="EMAIL" value="EMAIL" />
        </el-select>
      </el-form-item>
      <el-form-item label="标题"><el-input v-model="notifyForm.title" /></el-form-item>
      <el-form-item label="内容"><el-input v-model="notifyForm.content" type="textarea" :rows="4" /></el-form-item>
      <el-form-item label="接收人"><el-input v-model="notifyForm.receiversText" placeholder="多个接收人用逗号分隔" /></el-form-item>
      <el-form-item label="Webhook"><el-input v-model="notifyForm.webhookUrl" /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="notifyForm.status">
          <el-option label="启用" value="ENABLED" />
          <el-option label="禁用" value="DISABLED" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="notifyDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="saveNotify">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="changePasswordVisible" title="修改密码" width="460px">
    <el-form label-width="88px">
      <el-form-item label="原密码"><el-input v-model="changePasswordForm.oldPassword" type="password" show-password /></el-form-item>
      <el-form-item label="新密码"><el-input v-model="changePasswordForm.newPassword" type="password" show-password /></el-form-item>
      <el-form-item label="确认密码"><el-input v-model="changePasswordForm.confirmPassword" type="password" show-password /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="changePasswordVisible = false">取消</el-button>
      <el-button type="primary" @click="changeOwnPassword">保存</el-button>
    </template>
  </el-dialog>

  <el-drawer v-model="detailVisible" title="详情" size="46%">
    <pre class="detail">{{ JSON.stringify(detailRecord, null, 2) }}</pre>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, type Component } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowDown,
  ArrowLeft,
  Avatar,
  Bell,
  Check,
  Close,
  Connection,
  Collection,
  DataBoard,
  Delete,
  Download,
  Document,
  Edit,
  Files,
  FolderOpened,
  Lock,
  Menu as MenuIcon,
  Monitor,
  MostlyCloudy,
  OfficeBuilding,
  Plus,
  Refresh,
  RefreshRight,
  Search,
  Setting,
  Switch,
  SwitchButton,
  Tickets,
  Tools,
  Unlock,
  Upload,
  User,
  View
} from '@element-plus/icons-vue'
import {
  api,
  AUTH_EXPIRED_EVENT,
  clearToken,
  getToken,
  isAuthExpiredError,
  setToken,
  type ApiError,
  type AdminUser,
  type ConfigItem,
  type CurrentUser,
  type DashboardSummary,
  type Dept,
  type DictItem,
  type DictType,
  type ExcelErrorRecord,
  type ExcelTask,
  type FileRecord,
  type HealthStatus,
  type LoginLog,
  type LocalMessage,
  type MenuItem,
  type MqFailedMessage,
  type MqProviderStatus,
  type MqQueueInfo,
  type MqStats,
  type NotifyRecord,
  type NotifyTemplate,
  type OnlineSession,
  type OperationLog,
  type PageResult,
  type Role,
  type Tenant,
  type TraceDetail
} from './api/client'

type ViewName = 'dashboard' | 'system-tenants' | 'system-depts' | 'system-users' | 'system-roles' | 'system-menus' | 'system-dicts' | 'system-configs' | 'mq' | 'local' | 'notify' | 'excel' | 'files' | 'logs' | 'login-logs' | 'sessions' | 'trace' | 'monitor'
type NavItem = {
  index: string
  title: string
  icon: Component
  view?: ViewName
  children: NavItem[]
}
type NavOption = {
  value: ViewName
  label: string
}
type QuickNavItem = {
  title: string
  view: ViewName
}
type AlertItem = {
  title: string
  desc: string
  count: number
  type: 'danger' | 'warning' | 'success' | 'info' | 'primary'
  view: ViewName
}

const viewTitles: Record<ViewName, string> = {
  dashboard: '数据看板',
  'system-tenants': '租户管理',
  'system-depts': '部门管理',
  'system-users': '用户管理',
  'system-roles': '角色管理',
  'system-menus': '菜单管理',
  'system-dicts': '字典管理',
  'system-configs': '参数配置',
  mq: 'MQ 管理',
  local: '本地消息',
  notify: '通知中心',
  excel: 'Excel 中心',
  files: '文件中心',
  logs: '日志中心',
  'login-logs': '登录日志',
  sessions: '在线会话',
  trace: '链路追踪',
  monitor: '监控中心'
}

const componentViewMap: Record<string, ViewName> = {
  Dashboard: 'dashboard',
  SystemTenants: 'system-tenants',
  SystemDepts: 'system-depts',
  SystemUsers: 'system-users',
  SystemRoles: 'system-roles',
  SystemMenus: 'system-menus',
  SystemDicts: 'system-dicts',
  SystemConfigs: 'system-configs',
  Mq: 'mq',
  LocalMessage: 'local',
  Notify: 'notify',
  Excel: 'excel',
  Files: 'files',
  Logs: 'logs',
  LoginLogs: 'login-logs',
  Sessions: 'sessions',
  Trace: 'trace',
  Monitor: 'monitor'
}

const routeViewMap: Record<string, ViewName> = {
  dashboard: 'dashboard',
  'system/tenants': 'system-tenants',
  'system/depts': 'system-depts',
  'system/users': 'system-users',
  'system/roles': 'system-roles',
  'system/menus': 'system-menus',
  'system/dicts': 'system-dicts',
  'system/configs': 'system-configs',
  mq: 'mq',
  'local-message': 'local',
  local: 'local',
  notify: 'notify',
  excel: 'excel',
  files: 'files',
  logs: 'logs',
  'login-logs': 'login-logs',
  sessions: 'sessions',
  trace: 'trace',
  monitor: 'monitor'
}

const iconMap: Record<string, Component> = {
  Avatar,
  Bell,
  Collection,
  Connection,
  DataBoard,
  Document,
  Files,
  FolderOpened,
  Menu: MenuIcon,
  Monitor,
  MostlyCloudy,
  OfficeBuilding,
  Search,
  Setting,
  Tickets,
  Tools,
  User
}

const authed = ref(false)
const loginLoading = ref(false)
const authExpiredNotified = ref(false)
const currentUser = ref<CurrentUser>()
const loginForm = reactive({ username: '', password: '' })
const activeView = ref<ViewName>('dashboard')
const traceKeyword = ref('')
const dashboard = ref<DashboardSummary>()
const mqStats = ref<MqStats>()
const mqQueues = ref<MqQueueInfo[]>([])
const mqMessages = reactive<PageResult<MqFailedMessage>>({ records: [], total: 0, pageNum: 1, pageSize: 20, pages: 0 })
const selectedMqMessageIds = ref<number[]>([])
const localMessages = reactive<PageResult<LocalMessage>>({ records: [], total: 0, pageNum: 1, pageSize: 20, pages: 0 })
const notifyStats = ref<Record<string, number>>({})
const notifyTemplates = reactive<PageResult<NotifyTemplate>>({ records: [], total: 0, pageNum: 1, pageSize: 20, pages: 0 })
const notifyRecords = reactive<PageResult<NotifyRecord>>({ records: [], total: 0, pageNum: 1, pageSize: 20, pages: 0 })
const excelStats = ref<Record<string, number>>({})
const excelTasks = reactive<PageResult<ExcelTask>>({ records: [], total: 0, pageNum: 1, pageSize: 20, pages: 0 })
const excelErrors = ref<ExcelErrorRecord[]>([])
const fileStats = ref<Record<string, number>>({})
const fileRecords = reactive<PageResult<FileRecord>>({ records: [], total: 0, pageNum: 1, pageSize: 20, pages: 0 })
const logs = reactive<PageResult<OperationLog>>({ records: [], total: 0, pageNum: 1, pageSize: 20, pages: 0 })
const loginLogs = reactive<PageResult<LoginLog>>({ records: [], total: 0, pageNum: 1, pageSize: 20, pages: 0 })
const onlineSessions = ref<OnlineSession[]>([])
const traceDetail = ref<TraceDetail>()
const health = ref<HealthStatus>()
const users = reactive<PageResult<AdminUser>>({ records: [], total: 0, pageNum: 1, pageSize: 20, pages: 0 })
const tenants = ref<Tenant[]>([])
const depts = ref<Dept[]>([])
const roles = ref<Role[]>([])
const menus = ref<MenuItem[]>([])
const dictTypes = ref<DictType[]>([])
const dictItems = ref<DictItem[]>([])
const configs = ref<ConfigItem[]>([])
const jvm = ref<Record<string, unknown>>({})
const detailVisible = ref(false)
const detailRecord = ref<unknown>()
const userDialogVisible = ref(false)
const userDialogMode = ref<'create' | 'edit'>('create')
const editingUserId = ref<number>()
const tenantDialogVisible = ref(false)
const editingTenantId = ref<number>()
const deptDialogVisible = ref(false)
const editingDeptId = ref<number>()
const roleDialogVisible = ref(false)
const roleAuthVisible = ref(false)
const currentRole = ref<Role>()
const checkedMenuIds = ref<number[]>([])
const roleMenuTreeRef = ref()
const menuDialogVisible = ref(false)
const editingMenuId = ref<number>()
const dictTypeDialogVisible = ref(false)
const editingDictTypeId = ref<number>()
const dictItemDialogVisible = ref(false)
const editingDictItemId = ref<number>()
const configDialogVisible = ref(false)
const editingConfigId = ref<number>()
const notifyDialogVisible = ref(false)
const editingNotifyId = ref<number>()
const selectedDictCode = ref('')
const changePasswordVisible = ref(false)
const fileInputRef = ref<HTMLInputElement>()

const tenantForm = reactive({ tenantCode: '', tenantName: '', status: 'ENABLED' })
const deptForm = reactive({
  tenantId: 1 as number | undefined,
  parentId: 0 as number | undefined,
  deptName: '',
  sortOrder: 0,
  status: 'ENABLED'
})
const userForm = reactive({
  username: '',
  password: '',
  nickname: '',
  mobile: '',
  email: '',
  deptId: 1 as number | undefined,
  status: 'ENABLED',
  roleIds: [1] as number[]
})
const roleForm = reactive({ roleCode: '', roleName: '', sortOrder: 0, status: 'ENABLED' })
const menuForm = reactive({
  parentId: 0 as number | undefined,
  menuType: 'MENU',
  menuName: '',
  routePath: '',
  component: '',
  permission: '',
  icon: '',
  sortOrder: 0,
  visible: true
})
const dictTypeForm = reactive({ dictCode: '', dictName: '', status: 'ENABLED' })
const dictItemForm = reactive({ dictCode: '', itemLabel: '', itemValue: '', sortOrder: 0, status: 'ENABLED' })
const configForm = reactive({ configKey: '', configName: '', configValue: '', sensitive: false, remark: '' })
const notifyForm = reactive({
  templateCode: '',
  templateName: '',
  channel: 'LOG',
  title: '',
  content: '',
  receiversText: '',
  webhookUrl: '',
  status: 'ENABLED'
})
const changePasswordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})
const mqQuery = reactive({ status: '', traceId: '', pageNum: 1, pageSize: 20 })
const localQuery = reactive({ status: '', topic: '', pageNum: 1, pageSize: 20 })
const notifyQuery = reactive({ keyword: '', channel: '', status: '', pageNum: 1, pageSize: 20 })
const notifyRecordQuery = reactive<{ channel: string; success: boolean | ''; pageNum: number; pageSize: number }>({ channel: '', success: '', pageNum: 1, pageSize: 20 })
const excelQuery = reactive({ taskType: '', status: '', pageNum: 1, pageSize: 20 })
const fileQuery = reactive({ keyword: '', businessType: '', contentType: '', pageNum: 1, pageSize: 20 })
const logQuery = reactive({ logType: '', traceId: '', pageNum: 1, pageSize: 20 })
const loginLogQuery = reactive<{ username: string; success: boolean | ''; pageNum: number; pageSize: number }>({ username: '', success: '', pageNum: 1, pageSize: 20 })
const userQuery = reactive({ keyword: '', status: '', pageNum: 1, pageSize: 20 })
const deptQuery = reactive({ tenantId: 1 as number | undefined })

const viewTitle = computed(() => viewTitles[activeView.value])
const navMenus = computed(() => buildNavItems(currentUser.value?.menus ?? []))
const mobileNavOptions = computed(() => buildNavOptions(navMenus.value))
const userInitial = computed(() => (currentUser.value?.username || 'A').slice(0, 1).toUpperCase())
const quickNavItems = computed<QuickNavItem[]>(() => {
  const items: QuickNavItem[] = [
    { title: '首页', view: 'dashboard' },
    { title: '链路', view: 'trace' },
    { title: '消息', view: 'mq' },
    { title: '监控', view: 'monitor' }
  ]
  return items.filter((item) => canOpenView(item.view))
})
const greetingTitle = computed(() => {
  if (activeView.value !== 'dashboard') {
    return viewTitle.value
  }
  return `下午好，${currentUser.value?.nickname || currentUser.value?.username || 'admin'}`
})

const menuCount = computed(() => flattenMenus(menus.value).length)
const alertItems = computed<AlertItem[]>(() => {
  const data = dashboard.value
  if (!data) {
    return []
  }
  const items: AlertItem[] = [
    {
      title: 'MQ 待人工处理',
      desc: '失败消息已耗尽重试',
      count: metric(data.mq, 'exhausted'),
      type: 'danger',
      view: 'mq'
    },
    {
      title: 'MQ 待重试',
      desc: '失败消息等待重新投递',
      count: metric(data.mq, 'pending') + metric(data.mq, 'retrying'),
      type: 'warning',
      view: 'mq'
    },
    {
      title: '本地消息失败',
      desc: '本地消息表需要人工确认',
      count: metric(data.localMessage, 'failed'),
      type: 'danger',
      view: 'local'
    },
    {
      title: '通知发送失败',
      desc: '通知记录返回失败结果',
      count: metric(data.notifications, 'failedRecords'),
      type: 'warning',
      view: 'notify'
    },
    {
      title: 'Excel 失败任务',
      desc: '导入导出任务执行失败',
      count: metric(data.excel, 'failed'),
      type: 'warning',
      view: 'excel'
    }
  ]
  return items.filter((item) => item.count > 0 && canOpenView(item.view))
})
const alertCount = computed(() => alertItems.value.reduce((total, item) => total + item.count, 0))
const alertBadge = computed(() => alertCount.value > 99 ? '99+' : alertCount.value)

function can(permission: string) {
  return currentUser.value?.permissions?.includes(permission) ?? false
}

function canOpenView(view: ViewName) {
  return navMenus.value.length === 0 || isViewAllowed(view)
}

function metric(values: Record<string, number> | undefined, key: string) {
  return Number(values?.[key] ?? 0)
}

function handleAuthExpired(event: Event) {
  const detail = event instanceof CustomEvent ? event.detail as ApiError | undefined : undefined
  currentUser.value = undefined
  authed.value = false
  if (!authExpiredNotified.value) {
    authExpiredNotified.value = true
    ElMessage.warning(detail?.message || '登录已过期，请重新登录')
  }
}

onMounted(async () => {
  window.addEventListener(AUTH_EXPIRED_EVENT, handleAuthExpired)
  if (!getToken()) {
    return
  }
  try {
    currentUser.value = await api.me()
    authed.value = true
    syncActiveViewWithMenus()
    await refreshCurrent()
  } catch {
    clearToken()
  }
})

onUnmounted(() => {
  window.removeEventListener(AUTH_EXPIRED_EVENT, handleAuthExpired)
})

async function login() {
  loginLoading.value = true
  try {
    const response = await api.login({ ...loginForm, deviceId: 'admin-web' })
    setToken(response.accessToken)
    authExpiredNotified.value = false
    currentUser.value = { ...response.user, menus: response.user.menus ?? response.menus }
    authed.value = true
    syncActiveViewWithMenus()
    ElMessage.success('登录成功')
    await refreshCurrent()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败')
  } finally {
    loginLoading.value = false
  }
}

async function logout() {
  try {
    await api.logout()
  } finally {
    clearToken()
    authExpiredNotified.value = false
    authed.value = false
  }
}

function openChangePassword() {
  Object.assign(changePasswordForm, { oldPassword: '', newPassword: '', confirmPassword: '' })
  changePasswordVisible.value = true
}

async function changeOwnPassword() {
  const passwordError = validateStrongPassword(changePasswordForm.newPassword)
  if (passwordError !== true) {
    ElMessage.warning(passwordError)
    return
  }
  if (changePasswordForm.newPassword !== changePasswordForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  const message = await api.changePassword({
    oldPassword: changePasswordForm.oldPassword,
    newPassword: changePasswordForm.newPassword
  })
  ElMessage.success(message)
  changePasswordVisible.value = false
  clearToken()
  authExpiredNotified.value = false
  currentUser.value = undefined
  authed.value = false
}

async function refreshCurrent() {
  try {
    if (activeView.value === 'dashboard') await loadDashboard()
    if (activeView.value === 'system-tenants') await loadTenants()
    if (activeView.value === 'system-depts') await loadDepts()
    if (activeView.value === 'system-users') await loadUsers()
    if (activeView.value === 'system-roles') await loadRoles()
    if (activeView.value === 'system-menus') await loadMenus()
    if (activeView.value === 'system-dicts') await loadDicts()
    if (activeView.value === 'system-configs') await loadConfigs()
    if (activeView.value === 'mq') await loadMq()
    if (activeView.value === 'local') await loadLocal()
    if (activeView.value === 'notify') await loadNotify()
    if (activeView.value === 'excel') await loadExcel()
    if (activeView.value === 'files') await loadFiles()
    if (activeView.value === 'logs') await loadLogs()
    if (activeView.value === 'login-logs') await loadLoginLogs()
    if (activeView.value === 'sessions') await loadSessions()
    if (activeView.value === 'trace') await loadTrace()
    if (activeView.value === 'monitor') await loadMonitor()
  } catch (error) {
    if (isAuthExpiredError(error)) {
      return
    }
    ElMessage.error(error instanceof Error ? error.message : '加载失败')
  }
}

async function loadDashboard() {
  dashboard.value = await api.dashboard()
}

async function loadTenants() {
  tenants.value = await api.tenants()
  if (!deptQuery.tenantId && tenants.value.length) {
    deptQuery.tenantId = tenants.value[0].id
  }
}

async function loadDepts() {
  if (tenants.value.length === 0) {
    await loadTenants()
  }
  depts.value = await api.depts(deptQuery.tenantId)
}

async function loadUsers() {
  if (roles.value.length === 0) {
    await loadRoles()
  }
  if (depts.value.length === 0) {
    await loadDepts()
  }
  const page = await api.users(userQuery)
  Object.assign(users, page)
}

async function loadRoles() {
  roles.value = await api.roles()
  if (menus.value.length === 0) {
    await loadMenus()
  }
}

async function loadMenus() {
  menus.value = await api.menus()
}

async function loadDicts(dictCode?: string) {
  dictTypes.value = await api.dictTypes()
  selectedDictCode.value = dictCode || selectedDictCode.value || dictTypes.value[0]?.dictCode || ''
  dictItems.value = await api.dictItems(selectedDictCode.value)
}

async function loadConfigs() {
  configs.value = await api.configs()
}

async function loadMq() {
  const [stats, queues, page] = await Promise.all([
    api.mqStats(),
    api.mqQueues(),
    api.mqFailedMessages(mqQuery)
  ])
  mqStats.value = stats
  mqQueues.value = queues
  Object.assign(mqMessages, page)
}

async function loadLocal() {
  const page = await api.localMessages(localQuery)
  Object.assign(localMessages, page)
}

async function loadNotify() {
  notifyStats.value = await api.notifyStats()
  const templates = await api.notifyTemplates(notifyQuery)
  Object.assign(notifyTemplates, templates)
  const records = await api.notifyRecords({
    channel: notifyRecordQuery.channel,
    success: notifyRecordQuery.success === '' ? undefined : notifyRecordQuery.success,
    pageNum: notifyRecordQuery.pageNum,
    pageSize: notifyRecordQuery.pageSize
  })
  Object.assign(notifyRecords, records)
}

async function loadExcel() {
  excelStats.value = await api.excelStats()
  const page = await api.excelTasks(excelQuery)
  Object.assign(excelTasks, page)
}

async function loadFiles() {
  fileStats.value = await api.fileStats()
  const page = await api.files(fileQuery)
  Object.assign(fileRecords, page)
}

async function loadLogs() {
  const page = await api.logs(logQuery)
  Object.assign(logs, page)
}

async function loadLoginLogs() {
  const params = {
    username: loginLogQuery.username,
    success: loginLogQuery.success === '' ? undefined : loginLogQuery.success,
    pageNum: loginLogQuery.pageNum,
    pageSize: loginLogQuery.pageSize
  }
  const page = await api.loginLogs(params)
  Object.assign(loginLogs, page)
}

async function loadSessions() {
  onlineSessions.value = await api.sessions()
}

async function loadTrace() {
  const traceId = logQuery.traceId.trim()
  if (!traceId) {
    traceDetail.value = undefined
    return
  }
  traceDetail.value = await api.traceDetail(traceId)
  Object.assign(logs, {
    records: traceDetail.value.logs,
    total: traceDetail.value.logs.length,
    pageNum: 1,
    pageSize: traceDetail.value.logs.length || 20,
    pages: traceDetail.value.logs.length ? 1 : 0
  })
}

async function loadMonitor() {
  const [healthData, jvmData] = await Promise.all([
    api.monitorHealth(),
    api.monitorJvm()
  ])
  health.value = healthData
  jvm.value = jvmData
}

function openCreateTenant() {
  editingTenantId.value = undefined
  Object.assign(tenantForm, { tenantCode: '', tenantName: '', status: 'ENABLED' })
  tenantDialogVisible.value = true
}

function openEditTenant(row: Tenant) {
  editingTenantId.value = row.id
  Object.assign(tenantForm, {
    tenantCode: row.tenantCode,
    tenantName: row.tenantName,
    status: row.status || 'ENABLED'
  })
  tenantDialogVisible.value = true
}

async function saveTenant() {
  if (editingTenantId.value) {
    await api.updateTenant(editingTenantId.value, tenantForm)
    ElMessage.success('已更新')
  } else {
    await api.createTenant(tenantForm)
    ElMessage.success('已创建')
  }
  tenantDialogVisible.value = false
  await loadTenants()
}

async function deleteTenant(row: Tenant) {
  await confirmDelete(`删除租户 ${row.tenantName}？`)
  await api.deleteTenant(row.id)
  ElMessage.success('已删除')
  await loadTenants()
}

function openCreateDept(parent?: Dept) {
  editingDeptId.value = undefined
  Object.assign(deptForm, {
    tenantId: parent?.tenantId ?? deptQuery.tenantId ?? 1,
    parentId: parent?.id ?? 0,
    deptName: '',
    sortOrder: 0,
    status: 'ENABLED'
  })
  deptDialogVisible.value = true
}

function openEditDept(row: Dept) {
  editingDeptId.value = row.id
  Object.assign(deptForm, {
    tenantId: row.tenantId,
    parentId: row.parentId ?? 0,
    deptName: row.deptName,
    sortOrder: row.sortOrder ?? 0,
    status: row.status || 'ENABLED'
  })
  deptDialogVisible.value = true
}

async function saveDept() {
  if (editingDeptId.value) {
    await api.updateDept(editingDeptId.value, deptForm)
    ElMessage.success('已更新')
  } else {
    await api.createDept(deptForm)
    ElMessage.success('已创建')
  }
  deptDialogVisible.value = false
  deptQuery.tenantId = deptForm.tenantId
  await loadDepts()
}

async function deleteDept(row: Dept) {
  await confirmDelete(`删除部门 ${row.deptName} 及其子部门？`)
  await api.deleteDept(row.id)
  ElMessage.success('已删除')
  await loadDepts()
}

async function createUser() {
  const passwordError = validateStrongPassword(userForm.password)
  if (passwordError !== true) {
    ElMessage.warning(passwordError)
    return
  }
  await api.createUser(userForm)
  userDialogVisible.value = false
  resetUserForm()
  ElMessage.success('已创建')
  await loadUsers()
}

async function saveUser() {
  if (userDialogMode.value === 'create') {
    await createUser()
    return
  }
  if (!editingUserId.value) return
  await api.updateUser(editingUserId.value, userForm)
  userDialogVisible.value = false
  ElMessage.success('已更新')
  await loadUsers()
}

function openCreateUser() {
  userDialogMode.value = 'create'
  editingUserId.value = undefined
  resetUserForm()
  userDialogVisible.value = true
}

function openEditUser(row: AdminUser) {
  userDialogMode.value = 'edit'
  editingUserId.value = row.id
  Object.assign(userForm, {
    username: row.username,
    password: '',
    nickname: row.nickname || '',
    mobile: row.mobile || '',
    email: row.email || '',
    deptId: row.deptId || 1,
    status: row.status || 'ENABLED',
    roleIds: row.roleIds?.length ? row.roleIds : [1]
  })
  userDialogVisible.value = true
}

function resetUserForm() {
  Object.assign(userForm, {
    username: '',
    password: '',
    nickname: '',
    mobile: '',
    email: '',
    deptId: 1,
    status: 'ENABLED',
    roleIds: [1]
  })
}

async function resetUserPassword(id: number) {
  const result = await ElMessageBox.prompt('请输入新密码', '重置密码', {
    inputType: 'password',
    inputPlaceholder: '至少 8 位，包含大小写、数字和特殊字符',
    confirmButtonText: '确认重置',
    cancelButtonText: '取消',
    inputValidator: validateStrongPassword
  })
  await api.resetPassword(id, String(result.value || ''))
  ElMessage.success('密码已重置')
}

async function unlockUser(row: AdminUser) {
  await confirmAction(`解锁用户 ${row.username}？`, '解锁用户')
  const message = await api.unlockUser(row.id)
  ElMessage.success(message)
  await loadUsers()
}

function validateStrongPassword(value: string) {
  const password = String(value || '')
  if (!password) return '请输入新密码'
  if (password.length < 8) return '密码长度至少8位'
  if (password.length > 64) return '密码长度不能超过64位'
  if (!/[a-z]/.test(password)) return '密码必须包含小写字母'
  if (!/[A-Z]/.test(password)) return '密码必须包含大写字母'
  if (!/\d/.test(password)) return '密码必须包含数字'
  if (!/[@$!%*?&#^()_+\-=\[\]{}|]/.test(password)) return '密码必须包含特殊字符'
  return true
}

async function toggleUser(row: AdminUser) {
  await api.updateUserStatus(row.id, row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED')
  await loadUsers()
}

async function deleteUser(row: AdminUser) {
  await confirmDelete(`删除用户 ${row.username}？`)
  await api.deleteUser(row.id)
  ElMessage.success('已删除')
  await loadUsers()
}

async function kickSession(row: OnlineSession) {
  await confirmAction(`强制下线 ${row.username || row.userId} 的 ${row.deviceId} 会话？`, '强制下线')
  const message = await api.kickSession(row.userId, row.deviceId)
  ElMessage.success(message)
  await loadSessions()
}

function openCreateRole() {
  currentRole.value = undefined
  Object.assign(roleForm, { roleCode: '', roleName: '', sortOrder: 0, status: 'ENABLED' })
  roleDialogVisible.value = true
}

function openEditRole(row: Role) {
  currentRole.value = row
  Object.assign(roleForm, {
    roleCode: row.roleCode,
    roleName: row.roleName,
    sortOrder: row.sortOrder ?? 0,
    status: row.status || 'ENABLED'
  })
  roleDialogVisible.value = true
}

async function saveRole() {
  if (currentRole.value) {
    await api.updateRole(currentRole.value.id, roleForm)
    ElMessage.success('已更新')
  } else {
    await api.createRole(roleForm)
    ElMessage.success('已创建')
  }
  roleDialogVisible.value = false
  await loadRoles()
}

async function deleteRole(row: Role) {
  await confirmDelete(`删除角色 ${row.roleName}？`)
  await api.deleteRole(row.id)
  ElMessage.success('已删除')
  await loadRoles()
}

async function openRoleAuth(row: Role) {
  currentRole.value = row
  if (menus.value.length === 0) {
    await loadMenus()
  }
  checkedMenuIds.value = await api.roleMenuIds(row.id)
  roleAuthVisible.value = true
  await nextTick()
  roleMenuTreeRef.value?.setCheckedKeys?.(checkedMenuIds.value, false)
}

async function saveRoleAuth() {
  if (!currentRole.value) return
  const checked = roleMenuTreeRef.value?.getCheckedKeys?.(false) ?? []
  const halfChecked = roleMenuTreeRef.value?.getHalfCheckedKeys?.() ?? []
  const menuIds = Array.from(new Set([...checked, ...halfChecked])).map(Number)
  await api.updateRoleMenus(currentRole.value.id, menuIds)
  ElMessage.success('已保存授权')
  roleAuthVisible.value = false
}

function openCreateMenu(parent?: MenuItem) {
  editingMenuId.value = undefined
  Object.assign(menuForm, {
    parentId: parent?.id ?? 0,
    menuType: 'MENU',
    menuName: '',
    routePath: '',
    component: '',
    permission: '',
    icon: '',
    sortOrder: 0,
    visible: true
  })
  menuDialogVisible.value = true
}

function openEditMenu(row: MenuItem) {
  editingMenuId.value = row.id
  Object.assign(menuForm, {
    parentId: row.parentId ?? 0,
    menuType: row.menuType || 'MENU',
    menuName: row.menuName,
    routePath: row.routePath || '',
    component: row.component || '',
    permission: row.permission || '',
    icon: row.icon || '',
    sortOrder: row.sortOrder ?? 0,
    visible: row.visible !== false
  })
  menuDialogVisible.value = true
}

async function saveMenu() {
  if (editingMenuId.value) {
    await api.updateMenu(editingMenuId.value, menuForm)
    ElMessage.success('已更新')
  } else {
    await api.createMenu(menuForm)
    ElMessage.success('已创建')
  }
  menuDialogVisible.value = false
  await loadMenus()
}

async function deleteMenu(row: MenuItem) {
  await confirmDelete(`删除菜单 ${row.menuName} 及其子菜单？`)
  await api.deleteMenu(row.id)
  ElMessage.success('已删除')
  await loadMenus()
}

async function retryMq(id: number) {
  const message = await api.retryMqMessage(id)
  ElMessage.success(message)
  await loadMq()
}

function handleMqSelectionChange(rows: MqFailedMessage[]) {
  selectedMqMessageIds.value = rows.map((row) => row.id)
}

async function batchRetryMq() {
  if (selectedMqMessageIds.value.length === 0) {
    ElMessage.warning('请选择要重发的消息')
    return
  }
  const result = await api.batchRetryMqMessages(
    selectedMqMessageIds.value,
    currentUser.value?.username || 'admin',
    '批量重发'
  )
  if (result.failed > 0) {
    ElMessage.warning(`已提交 ${result.total} 条，成功 ${result.success} 条，失败 ${result.failed} 条`)
  } else {
    ElMessage.success(`已提交 ${result.total} 条，成功 ${result.success} 条`)
  }
  selectedMqMessageIds.value = []
  await loadMq()
}

async function manualSuccessMq(row: MqFailedMessage) {
  const remark = await promptRemark(`人工补偿完成 ${row.messageId || row.id}`)
  const message = await api.manualSuccessMqMessage(row.id, {
    operator: currentUser.value?.username || 'admin',
    remark
  })
  ElMessage.success(message)
  await loadMq()
}

async function manualFailureMq(row: MqFailedMessage) {
  const remark = await promptRemark(`人工终止 ${row.messageId || row.id}`)
  const message = await api.manualFailureMqMessage(row.id, {
    operator: currentUser.value?.username || 'admin',
    remark
  })
  ElMessage.success(message)
  await loadMq()
}

async function deleteMq(row: MqFailedMessage) {
  await confirmDelete(`删除 MQ 失败记录 ${row.messageId || row.id}？`)
  const message = await api.deleteMqMessage(row.id)
  ElMessage.success(message)
  await loadMq()
}

async function cleanMq() {
  const message = await api.cleanMqProcessed()
  ElMessage.success(message)
  await loadMq()
}

async function retryLocal() {
  const count = await api.retryDueLocalMessages()
  ElMessage.success(`已处理 ${count} 条`)
  await loadLocal()
}

async function markLocalSuccess(row: LocalMessage) {
  await confirmAction(`标记本地消息 ${row.messageId || row.id} 为成功？`, '确认标记')
  const message = await api.markLocalMessageSuccess(row.id)
  ElMessage.success(message)
  await loadLocal()
}

async function markLocalFailure(row: LocalMessage) {
  const reason = await promptRemark(`标记本地消息 ${row.messageId || row.id} 为失败`)
  const message = await api.markLocalMessageFailure(row.id, reason)
  ElMessage.success(message)
  await loadLocal()
}

async function deleteLocal(row: LocalMessage) {
  await confirmDelete(`删除本地消息 ${row.messageId || row.id}？`)
  const message = await api.deleteLocalMessage(row.id)
  ElMessage.success(message)
  await loadLocal()
}

function openCreateNotify() {
  editingNotifyId.value = undefined
  Object.assign(notifyForm, {
    templateCode: '',
    templateName: '',
    channel: 'LOG',
    title: '',
    content: '',
    receiversText: '',
    webhookUrl: '',
    status: 'ENABLED'
  })
  notifyDialogVisible.value = true
}

function openEditNotify(row: NotifyTemplate) {
  editingNotifyId.value = row.id
  Object.assign(notifyForm, {
    templateCode: row.templateCode,
    templateName: row.templateName,
    channel: row.channel || 'LOG',
    title: row.title || '',
    content: row.content || '',
    receiversText: row.receivers?.join(',') || '',
    webhookUrl: row.webhookUrl || '',
    status: row.status || 'ENABLED'
  })
  notifyDialogVisible.value = true
}

async function saveNotify() {
  const data = notifyPayload()
  if (editingNotifyId.value) {
    await api.updateNotifyTemplate(editingNotifyId.value, data)
    ElMessage.success('已更新')
  } else {
    await api.createNotifyTemplate(data)
    ElMessage.success('已创建')
  }
  notifyDialogVisible.value = false
  await loadNotify()
}

async function sendTestNotify(row: NotifyTemplate) {
  const record = await api.sendTestNotify(row.id, {
    receivers: row.receivers,
    webhookUrl: row.webhookUrl,
    templateParams: {
      module: 'admin-service',
      reason: 'test'
    }
  })
  if (record.success) {
    ElMessage.success(record.resultMessage || '已发送')
  } else {
    ElMessage.warning(record.resultMessage || '发送失败')
  }
  await loadNotify()
}

async function deleteNotify(row: NotifyTemplate) {
  await confirmDelete(`删除通知模板 ${row.templateName}？`)
  await api.deleteNotifyTemplate(row.id)
  ElMessage.success('已删除')
  await loadNotify()
}

function notifyPayload() {
  return {
    templateCode: notifyForm.templateCode,
    templateName: notifyForm.templateName,
    channel: notifyForm.channel,
    title: notifyForm.title,
    content: notifyForm.content,
    receivers: splitText(notifyForm.receiversText),
    webhookUrl: notifyForm.webhookUrl,
    status: notifyForm.status
  }
}

function splitText(value: string) {
  return value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

async function createExportTask() {
  const result = await api.createExportTask({ taskName: '用户清单导出任务', bizType: 'system-user' })
  ElMessage.success(`已生成 ${result.filename}`)
  await loadExcel()
}

async function createImportFailureTask() {
  const result = await api.createImportFailureTask({
    taskName: '用户导入失败任务',
    bizType: 'system-user',
    errorMessage: '模板表头不匹配'
  })
  ElMessage.warning(`已创建失败任务 ${result.taskId}`)
  await loadExcel()
}

async function loadExcelErrors(row: ExcelTask) {
  excelErrors.value = await api.excelErrors(row.id)
  detailRecord.value = {
    task: row,
    errors: excelErrors.value
  }
  detailVisible.value = true
}

function chooseFile() {
  fileInputRef.value?.click()
}

async function uploadSelectedFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) {
    return
  }
  const form = new FormData()
  form.append('file', file)
  if (fileQuery.businessType) {
    form.append('businessType', fileQuery.businessType)
  }
  const record = await api.uploadFile(form)
  input.value = ''
  ElMessage.success(`已上传 ${record.originalFilename}`)
  await loadFiles()
}

async function downloadFile(row: FileRecord) {
  const blob = await api.downloadFile(row.id)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = row.originalFilename || 'file'
  link.click()
  URL.revokeObjectURL(url)
}

async function deleteFile(row: FileRecord) {
  await confirmDelete(`删除文件 ${row.originalFilename}？`)
  await api.deleteFile(row.id)
  ElMessage.success('已删除')
  await loadFiles()
}

async function searchTrace() {
  const traceId = traceKeyword.value.trim()
  if (!traceId) return
  activeView.value = 'trace'
  logQuery.traceId = traceId
  logQuery.pageNum = 1
  await loadTrace()
}

function selectDict(row: DictType) {
  selectedDictCode.value = row.dictCode
  loadDicts(row.dictCode)
}

function openCreateDictType() {
  editingDictTypeId.value = undefined
  Object.assign(dictTypeForm, { dictCode: '', dictName: '', status: 'ENABLED' })
  dictTypeDialogVisible.value = true
}

function openEditDictType(row: DictType) {
  editingDictTypeId.value = row.id
  Object.assign(dictTypeForm, { dictCode: row.dictCode, dictName: row.dictName, status: row.status || 'ENABLED' })
  dictTypeDialogVisible.value = true
}

async function saveDictType() {
  if (editingDictTypeId.value) {
    await api.updateDictType(editingDictTypeId.value, dictTypeForm)
    ElMessage.success('已更新')
  } else {
    await api.createDictType(dictTypeForm)
    ElMessage.success('已创建')
  }
  dictTypeDialogVisible.value = false
  selectedDictCode.value = dictTypeForm.dictCode
  await loadDicts(dictTypeForm.dictCode)
}

async function deleteDictType(row: DictType) {
  await confirmDelete(`删除字典 ${row.dictName} 及其字典项？`)
  await api.deleteDictType(row.id)
  ElMessage.success('已删除')
  selectedDictCode.value = ''
  await loadDicts()
}

function openCreateDictItem() {
  editingDictItemId.value = undefined
  Object.assign(dictItemForm, {
    dictCode: selectedDictCode.value,
    itemLabel: '',
    itemValue: '',
    sortOrder: 0,
    status: 'ENABLED'
  })
  dictItemDialogVisible.value = true
}

function openEditDictItem(row: DictItem) {
  editingDictItemId.value = row.id
  Object.assign(dictItemForm, {
    dictCode: row.dictCode,
    itemLabel: row.itemLabel,
    itemValue: row.itemValue,
    sortOrder: row.sortOrder ?? 0,
    status: row.status || 'ENABLED'
  })
  dictItemDialogVisible.value = true
}

async function saveDictItem() {
  if (editingDictItemId.value) {
    await api.updateDictItem(editingDictItemId.value, dictItemForm)
    ElMessage.success('已更新')
  } else {
    await api.createDictItem(dictItemForm)
    ElMessage.success('已创建')
  }
  dictItemDialogVisible.value = false
  selectedDictCode.value = dictItemForm.dictCode
  await loadDicts(dictItemForm.dictCode)
}

async function deleteDictItem(row: DictItem) {
  await confirmDelete(`删除字典项 ${row.itemLabel}？`)
  await api.deleteDictItem(row.id)
  ElMessage.success('已删除')
  await loadDicts(row.dictCode)
}

function openCreateConfig() {
  editingConfigId.value = undefined
  Object.assign(configForm, { configKey: '', configName: '', configValue: '', sensitive: false, remark: '' })
  configDialogVisible.value = true
}

function openEditConfig(row: ConfigItem) {
  editingConfigId.value = row.id
  Object.assign(configForm, {
    configKey: row.configKey,
    configName: row.configName,
    configValue: row.configValue || '',
    sensitive: row.sensitive,
    remark: row.remark || ''
  })
  configDialogVisible.value = true
}

async function saveConfig() {
  if (editingConfigId.value) {
    await api.updateConfig(editingConfigId.value, configForm)
    ElMessage.success('已更新')
  } else {
    await api.createConfig(configForm)
    ElMessage.success('已创建')
  }
  configDialogVisible.value = false
  await loadConfigs()
}

async function deleteConfig(row: ConfigItem) {
  await confirmDelete(`删除参数 ${row.configKey}？`)
  await api.deleteConfig(row.id)
  ElMessage.success('已删除')
  await loadConfigs()
}

function openDetail(row: unknown) {
  detailRecord.value = row
  detailVisible.value = true
}

function buildNavItems(items: MenuItem[]): NavItem[] {
  return [...items]
    .filter((item) => item.visible !== false && item.menuType !== 'BUTTON')
    .sort((left, right) => (left.sortOrder ?? 0) - (right.sortOrder ?? 0))
    .map((item): NavItem | undefined => {
      const children = buildNavItems(item.children ?? [])
      const view = resolveMenuView(item)
      if (!view && children.length === 0) {
        return undefined
      }
      return {
        index: view ?? item.routePath ?? item.component ?? String(item.id),
        title: item.menuName,
        icon: resolveMenuIcon(item.icon),
        view,
        children
      }
    })
    .filter((item): item is NavItem => Boolean(item))
}

function resolveMenuView(item: MenuItem): ViewName | undefined {
  if (item.component && componentViewMap[item.component]) {
    return componentViewMap[item.component]
  }
  if (item.routePath && routeViewMap[item.routePath]) {
    return routeViewMap[item.routePath]
  }
  return undefined
}

function resolveMenuIcon(icon?: string) {
  return icon ? iconMap[icon] ?? Document : Document
}

function isViewName(index: string): index is ViewName {
  return index in viewTitles
}

function isViewAllowed(view: ViewName, items = navMenus.value): boolean {
  return items.some((item) => item.view === view || isViewAllowed(view, item.children))
}

function firstNavView(items = navMenus.value): ViewName | undefined {
  for (const item of items) {
    if (item.view) {
      return item.view
    }
    const childView = firstNavView(item.children)
    if (childView) {
      return childView
    }
  }
  return undefined
}

function syncActiveViewWithMenus() {
  if (navMenus.value.length === 0 || isViewAllowed(activeView.value)) {
    return
  }
  const nextView = firstNavView()
  if (nextView) {
    activeView.value = nextView
  }
}

function selectView(index: string) {
  if (!isViewName(index)) {
    return
  }
  activeView.value = index
  refreshCurrent()
}

function buildNavOptions(items: NavItem[], prefix = ''): NavOption[] {
  return items.flatMap((item) => {
    const label = prefix ? `${prefix} / ${item.title}` : item.title
    const current = item.view ? [{ value: item.view, label }] : []
    return [...current, ...buildNavOptions(item.children, label)]
  })
}

function flattenMenus(items: MenuItem[]): MenuItem[] {
  return items.flatMap((item) => [item, ...flattenMenus(item.children ?? [])])
}

function flattenDepts(items: Dept[]): Dept[] {
  return items.flatMap((item) => [item, ...flattenDepts(item.children ?? [])])
}

function isCurrentSession(row: OnlineSession) {
  return currentUser.value?.userId === row.userId && row.deviceId === 'admin-web'
}

function menuParentOptions() {
  return [{ id: 0, menuName: '根菜单' }, ...flattenMenus(menus.value)]
}

function deptParentOptions() {
  return [{ id: 0, deptName: '根部门' }, ...flattenDepts(depts.value)]
}

async function confirmDelete(message: string) {
  await ElMessageBox.confirm(message, '确认删除', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
}

async function confirmAction(message: string, title = '确认操作') {
  await ElMessageBox.confirm(message, title, {
    type: 'warning',
    confirmButtonText: '确认',
    cancelButtonText: '取消'
  })
}

async function promptRemark(title: string) {
  const result = await ElMessageBox.prompt('请输入处理备注', title, {
    inputType: 'textarea',
    inputPlaceholder: '处理原因、外部工单号或补偿说明',
    confirmButtonText: '确认',
    cancelButtonText: '取消'
  })
  return String(result.value || '').trim()
}

function mqStatusType(status?: string) {
  if (status === 'EXHAUSTED') return 'danger'
  if (status === 'MANUAL' || status === 'SUCCESS') return 'success'
  if (status === 'RETRYING') return 'warning'
  return 'info'
}

function providerTagType(provider: MqProviderStatus) {
  if (provider.active && provider.available) return 'success'
  if (provider.active) return 'warning'
  if (provider.available) return 'primary'
  return 'info'
}

function localStatusType(status?: string) {
  if (status === 'FAILED') return 'danger'
  if (status === 'SUCCESS') return 'success'
  if (status === 'PROCESSING') return 'warning'
  return 'info'
}

function statusType(status?: string) {
  if (status === 'FAILED' || status === 'EXHAUSTED') return 'danger'
  if (status === 'SUCCESS' || status === 'MANUAL') return 'success'
  if (status === 'RETRYING' || status === 'PROCESSING') return 'warning'
  return 'info'
}

function traceSourceType(source?: string) {
  if (source === 'MQ') return 'warning'
  if (source === 'LOCAL_MESSAGE') return 'success'
  return 'info'
}

function formatRuntime(value: unknown) {
  if (typeof value === 'number' && value > 1024 * 1024) {
    return `${(value / 1024 / 1024).toFixed(1)} MB`
  }
  return String(value)
}

function formatBytes(value?: number) {
  const size = Number(value || 0)
  if (size >= 1024 * 1024 * 1024) {
    return `${(size / 1024 / 1024 / 1024).toFixed(1)} GB`
  }
  if (size >= 1024 * 1024) {
    return `${(size / 1024 / 1024).toFixed(1)} MB`
  }
  if (size >= 1024) {
    return `${(size / 1024).toFixed(1)} KB`
  }
  return `${size} B`
}

function formatLoginTime(value: number) {
  if (!value) {
    return '-'
  }
  return new Date(value).toLocaleString()
}

function formatTtl(value: number) {
  if (value < 0) {
    return '-'
  }
  const hours = Math.floor(value / 3600)
  const minutes = Math.floor((value % 3600) / 60)
  const seconds = value % 60
  if (hours > 0) {
    return `${hours}h ${minutes}m`
  }
  if (minutes > 0) {
    return `${minutes}m ${seconds}s`
  }
  return `${seconds}s`
}

function formatHealthDetails(details?: Record<string, unknown>) {
  if (!details || Object.keys(details).length === 0) {
    return '-'
  }
  return Object.entries(details)
    .slice(0, 3)
    .map(([key, value]) => `${key}: ${formatRuntime(value)}`)
    .join(' · ')
}
</script>

<style scoped>
.login-page {
  display: grid;
  place-items: center;
  min-height: 100vh;
  background: #fafafa;
}

.login-panel {
  width: min(420px, calc(100vw - 32px));
  padding: 28px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 16px 40px rgb(15 23 42 / 7%);
}

.login-form {
  margin-top: 24px;
}

.login-button {
  width: 100%;
}

.auth-title {
  margin-bottom: 12px;
  color: #18181b;
  font-weight: 700;
}

.shell {
  flex-direction: column;
  min-height: 100vh;
  background: #fff;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
  padding: 0 16px;
  border-bottom: 1px solid #f1f1f1;
  background: #fff;
}

.header-left,
.header-actions,
.top-nav,
.user-chip {
  display: flex;
  align-items: center;
}

.header-left {
  gap: 26px;
}

.header-actions {
  gap: 10px;
}

.header-trace {
  width: 220px;
}

.top-nav {
  gap: 24px;
}

.top-nav-item {
  padding: 0;
  border: 0;
  background: transparent;
  color: #202124;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
}

.top-nav-item.active {
  color: #111827;
}

.alert-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.alert-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 8px;
  border-bottom: 1px solid #f1f1f1;
  color: #202124;
  font-size: 15px;
  font-weight: 800;
}

.alert-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  min-height: 54px;
  padding: 10px 12px;
  border: 1px solid #f1f1f1;
  border-radius: 8px;
  background: #fff;
  color: #202124;
  text-align: left;
  cursor: pointer;
  transition: border-color 120ms ease, background-color 120ms ease;
}

.alert-item:hover {
  border-color: #dcd7ff;
  background: #faf9ff;
}

.alert-item span {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.alert-item strong {
  font-size: 14px;
  line-height: 1.2;
}

.alert-item small {
  color: #71717a;
  font-size: 12px;
  line-height: 1.3;
}

.user-chip {
  gap: 8px;
  height: 34px;
  padding: 0 12px 0 6px;
  border-radius: 999px;
  background: #f5f5f5;
  color: #4b5563;
  font-weight: 600;
}

.user-avatar {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: #5fd4c9;
  color: #fff;
  font-size: 13px;
  font-weight: 800;
}

.shell-body {
  min-height: calc(100vh - 60px);
}

.mobile-view-switch {
  display: none;
  padding: 10px 14px;
  border-bottom: 1px solid #f1f1f1;
  background: #fff;
}

.mobile-view-switch :deep(.el-select) {
  width: 100%;
}

.sidebar {
  position: relative;
  width: 250px !important;
  padding: 24px 16px 64px 0;
  border-right: 1px solid #f1f1f1;
  background: #fff;
  color: #202124;
}

.sidebar-section {
  margin: 0 16px 10px;
  color: #9ca3af;
  font-size: 14px;
  font-weight: 500;
}

.collapse-button {
  position: absolute;
  right: 20px;
  bottom: 16px;
  left: 16px;
}

.collapse-button :deep(.el-button) {
  width: 100%;
  justify-content: center;
  background: #fff;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: auto;
  padding: 0;
  border-bottom: 0;
}

.brand.large {
  height: auto;
  padding: 0;
}

.brand-mark {
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  border: 0;
  background: linear-gradient(135deg, #4fd1ff, #8b5cf6 52%, #ff65b3);
  color: #fff;
  font-weight: 800;
  font-size: 13px;
}

.brand-name {
  color: #202124;
  font-size: 20px;
  font-weight: 800;
  line-height: 1.2;
}

.brand-subtitle {
  margin-top: 4px;
  color: #71717a;
  font-size: 12px;
}

.nav {
  --el-menu-bg-color: transparent;
  --el-menu-hover-bg-color: #f3f4f6;
  --el-menu-active-color: #8b5cf6;
  --el-menu-text-color: #202124;
  --el-menu-level-padding: 12px;

  height: calc(100vh - 168px);
  padding: 0 16px 18px 0;
  border-right: 0;
  background: transparent;
  overflow-y: auto;
}

.nav :deep(.el-menu-item),
.nav :deep(.el-sub-menu__title) {
  position: relative;
  height: 42px;
  margin: 4px 0;
  border-radius: 0 18px 18px 0;
  color: #202124;
  font-size: 16px;
  font-weight: 700;
  line-height: 42px;
  transition: background-color 120ms ease, color 120ms ease;
}

.nav :deep(.el-menu-item .el-icon),
.nav :deep(.el-sub-menu__title .el-icon) {
  width: 16px;
  margin-right: 12px;
  color: #70757a;
  font-size: 18px;
}

.nav :deep(.el-sub-menu .el-menu) {
  padding: 2px 0 8px;
  background: transparent;
}

.nav :deep(.el-sub-menu .el-menu-item) {
  height: 34px;
  margin: 2px 0 2px 28px;
  padding-left: 16px !important;
  color: #202124;
  font-size: 15px;
  font-weight: 700;
  line-height: 34px;
}

.nav :deep(.el-sub-menu .el-menu-item::before) {
  content: '';
  position: absolute;
  left: 7px;
  width: 1px;
  height: 20px;
  border-radius: 999px;
  background: #e5e7eb;
}

.nav :deep(.el-menu-item.is-active),
.nav :deep(.el-sub-menu__title:hover),
.nav :deep(.el-menu-item:hover) {
  color: #8b5cf6;
  background: #f2f0ff;
}

.nav :deep(.el-menu-item.is-active) {
  color: #8b5cf6;
  background: #f2f0ff;
}

.nav :deep(.el-menu-item.is-active::after) {
  display: none;
}

.nav :deep(.el-menu-item.is-active .el-icon),
.nav :deep(.el-sub-menu__title:hover .el-icon),
.nav :deep(.el-menu-item:hover .el-icon) {
  color: #8b5cf6;
}

.nav :deep(.el-sub-menu.is-active > .el-sub-menu__title) {
  color: #18181b;
}

.nav :deep(.el-sub-menu__icon-arrow) {
  right: 14px;
  color: #70757a;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 86px;
  padding: 0 24px;
  border-bottom: 0;
  background: #fff;
}

.file-input {
  display: none;
}

.page-title {
  color: #202124;
  font-size: 28px;
  font-weight: 800;
}

.page-meta {
  margin-top: 4px;
  color: #9ca3af;
  font-size: 13px;
}

.actions,
.section-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.section-head {
  justify-content: space-between;
}

.content {
  padding: 12px 28px 28px;
  background: #fff;
}

.view {
  display: grid;
  gap: 16px;
  align-content: start;
  max-width: none;
  min-height: calc(100vh - 100px);
  margin: 0;
}

.view:has(> .el-card:only-child) {
  align-content: stretch;
}

.view:has(> .el-card:only-child) > .el-card {
  min-height: calc(100vh - 100px);
}

.two-columns {
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.3fr);
}

.metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.metrics.compact {
  grid-template-columns: repeat(4, minmax(150px, 1fr));
}

.metric {
  min-height: 88px;
  padding: 16px;
  border: 1px solid #ececec;
  border-radius: 8px;
  background: #fff;
}

.metric span {
  display: block;
  color: #71717a;
  font-size: 13px;
}

.metric strong {
  display: block;
  margin-top: 11px;
  color: #18181b;
  font-size: 26px;
  line-height: 1;
}

.module-grid,
.runtime-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 10px;
}

.module-item,
.runtime-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
  padding: 10px 12px;
  border: 1px solid #ececec;
  border-radius: 8px;
  background: #fff;
}

.runtime-item {
  align-items: flex-start;
  flex-direction: column;
  gap: 6px;
}

.runtime-item span {
  color: #71717a;
  font-size: 12px;
}

.runtime-item strong {
  max-width: 100%;
  overflow-wrap: anywhere;
  color: #18181b;
}

.runtime-item.table-name {
  grid-column: span 2;
}

.provider-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.health-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 10px;
}

.health-item {
  min-height: 82px;
  padding: 12px;
  border: 1px solid #ececec;
  border-radius: 8px;
  background: #fff;
}

.health-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  color: #18181b;
  font-weight: 650;
}

.health-detail {
  margin-top: 10px;
  color: #71717a;
  font-size: 12px;
  line-height: 1.55;
  overflow-wrap: anywhere;
}

.trace-timeline {
  padding: 8px 6px 0;
}

.trace-event {
  display: grid;
  gap: 8px;
}

.trace-event-head,
.trace-event-body {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.trace-event-body {
  color: #71717a;
  font-size: 13px;
}

.filter {
  width: 150px;
}

.filter.wide {
  width: 220px;
}

.pager {
  justify-content: flex-end;
  margin-top: 14px;
}

.detail {
  margin: 0;
  padding: 14px;
  min-height: 360px;
  overflow: auto;
  border-radius: 8px;
  border: 1px solid #27272a;
  background: #18181b;
  color: #e4e4e7;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.shell :deep(.el-card) {
  border: 1px solid #ececec;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgb(17 24 39 / 3%);
}

.shell :deep(.el-card__header) {
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
  color: #202124;
  font-size: 15px;
  font-weight: 800;
}

.shell :deep(.el-card__body) {
  padding: 14px 16px 16px;
}

.shell :deep(.el-table) {
  --el-table-header-bg-color: #fff;
  --el-table-header-text-color: #70757a;
  --el-table-row-hover-bg-color: #f7f7f7;
  border-radius: 8px;
  color: #303133;
  font-size: 15px;
}

.shell :deep(.el-table th.el-table__cell) {
  font-weight: 600;
}

.shell :deep(.el-input__wrapper),
.shell :deep(.el-select__wrapper),
.shell :deep(.el-textarea__inner),
.shell :deep(.el-input-number .el-input__wrapper) {
  border-radius: 8px;
  background: #f5f5f5;
  box-shadow: none;
}

.shell :deep(.el-input__wrapper.is-focus),
.shell :deep(.el-select__wrapper.is-focused),
.shell :deep(.el-textarea__inner:focus) {
  box-shadow: 0 0 0 1px #1677ff inset;
}

.shell :deep(.el-button) {
  border-radius: 8px;
  font-weight: 500;
}

.shell :deep(.el-button--primary) {
  --el-button-bg-color: #2563eb;
  --el-button-border-color: #2563eb;
  --el-button-hover-bg-color: #1d4ed8;
  --el-button-hover-border-color: #1d4ed8;
  --el-button-active-bg-color: #1e40af;
  --el-button-active-border-color: #1e40af;
}

.shell :deep(.el-tag) {
  border-radius: 999px;
}

@media (max-width: 1100px) {
  .two-columns {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 960px) {
  .header-trace,
  .top-nav {
    display: none;
  }

  .sidebar {
    width: 76px !important;
  }

  .brand-name,
  .brand-subtitle,
  .nav span {
    display: none;
  }

  .brand {
    justify-content: center;
    padding: 0;
  }

  .nav {
    padding: 12px 8px;
  }

  .nav :deep(.el-menu-item),
  .nav :deep(.el-sub-menu__title) {
    justify-content: center;
    padding: 0 !important;
  }

  .nav :deep(.el-menu-item .el-icon),
  .nav :deep(.el-sub-menu__title .el-icon) {
    margin-right: 0;
  }

  .metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .topbar {
    align-items: flex-start;
    height: auto;
    min-height: 84px;
    padding: 14px;
    gap: 12px;
    flex-direction: column;
  }

}

@media (max-width: 640px) {
  .app-header {
    padding: 0 10px;
  }

  .brand-name {
    display: none;
  }

  .header-actions {
    gap: 8px;
  }

  .header-actions :deep(.el-button.is-circle) {
    width: 34px;
    height: 34px;
  }

  .user-chip {
    max-width: 126px;
    padding-right: 10px;
  }

  .user-chip span:not(.user-avatar) {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .mobile-view-switch {
    display: block;
  }

  .shell-body {
    min-height: calc(100vh - 117px);
  }

  .sidebar {
    display: none;
  }

  .topbar {
    min-height: 70px;
    padding: 10px 14px 0;
  }

  .page-title {
    font-size: 24px;
  }

  .content {
    padding: 14px;
  }

  .metrics {
    grid-template-columns: 1fr;
  }

  .section-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .filter,
  .filter.wide {
    width: 100%;
  }
}
</style>
