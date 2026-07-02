#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${ADMIN_BASE_URL:-http://127.0.0.1:8081}"
USERNAME="${ADMIN_USERNAME:-admin}"
PASSWORD="${ADMIN_PASSWORD:-Admin@123}"
DEVICE_ID="${ADMIN_DEVICE_ID:-smoke-admin}"

BASE_URL="${BASE_URL}" USERNAME="${USERNAME}" PASSWORD="${PASSWORD}" DEVICE_ID="${DEVICE_ID}" node <<'NODE'
const baseUrl = process.env.BASE_URL.replace(/\/+$/, '')
const username = process.env.USERNAME
const password = process.env.PASSWORD
const deviceId = process.env.DEVICE_ID

async function request(path, options = {}) {
  const response = await fetch(`${baseUrl}${path}`, options)
  const text = await response.text()
  let body
  try {
    body = text ? JSON.parse(text) : null
  } catch {
    body = text
  }
  return { status: response.status, body }
}

function isOk(result) {
  return result.status === 200 && result.body && result.body.code === 200
}

const login = await request('/admin/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username, password, deviceId })
})

if (!isOk(login) || !login.body.data || !login.body.data.accessToken) {
  console.error(`login failed: http=${login.status}, code=${login.body?.code}, message=${login.body?.message}`)
  process.exit(1)
}

const token = login.body.data.accessToken
const endpoints = [
  '/admin/auth/me',
  '/admin/dashboard',
  '/admin/sessions?pageNum=1&pageSize=5',
  '/admin/mq/stats',
  '/admin/local-messages?pageNum=1&pageSize=5',
  '/admin/logs?pageNum=1&pageSize=5',
  '/admin/monitor/health',
  '/admin/notify/stats',
  '/admin/files?pageNum=1&pageSize=5',
  '/admin/excel/tasks?pageNum=1&pageSize=5'
]

let failed = false
for (const endpoint of endpoints) {
  const result = await request(endpoint, {
    headers: { Authorization: `Bearer ${token}` }
  })
  if (isOk(result)) {
    console.log(`OK ${endpoint}`)
  } else {
    failed = true
    console.error(`FAIL ${endpoint}: http=${result.status}, code=${result.body?.code}, message=${result.body?.message}`)
  }
}

await request('/admin/auth/logout', {
  method: 'POST',
  headers: { Authorization: `Bearer ${token}` }
})

if (failed) {
  process.exit(1)
}
console.log('admin smoke passed')
NODE
