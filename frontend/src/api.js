const API_URL = import.meta.env.VITE_API_URL || '/api'

async function request(path, options = {}) {
  const response = await fetch(`${API_URL}${path}`, {
    ...options,
    headers: options.body ? { 'Content-Type': 'application/json', ...options.headers } : options.headers,
  })

  if (!response.ok) {
    let message = `Ошибка ${response.status}`
    try {
      const body = await response.json()
      message = body.message || message
    } catch {
      // Keep the HTTP status when the backend did not return JSON.
    }
    throw new Error(message)
  }

  if (response.status === 204) return null
  return response.json()
}

export const api = {
  rooms: {
    all: () => request('/rooms'),
    available: ({ startsAt, endsAt, attendeesCount }) => {
      const params = new URLSearchParams({ startsAt, endsAt, attendeesCount })
      return request(`/rooms/available?${params}`)
    },
    create: (data) => request('/rooms', { method: 'POST', body: JSON.stringify(data) }),
    remove: (id) => request(`/rooms/${id}`, { method: 'DELETE' }),
  },
  requests: {
    assignRule: (id, ruleId) => request(`/requests/${id}/rule-assignment`, { method: 'PATCH', body: JSON.stringify({ ruleId }) }),
    reviseRule: (id, data) => request(`/requests/${id}/rule`, { method: 'PATCH', body: JSON.stringify(data) }),
    popularTags: () => request('/requests/tags/popular'),
    all: (status) => request(`/requests${status && status !== 'ALL' ? `?status=${status}` : ''}`),
    search: (filters) => {
      const params = new URLSearchParams(Object.entries(filters)
        .filter(([, value]) => value !== undefined && value !== null && value !== ''))
      return request(`/requests/search?${params}`)
    },
    create: (data) => request('/requests', { method: 'POST', body: JSON.stringify(data) }),
    approve: (id) => request(`/requests/${id}/approve`, { method: 'PATCH' }),
    reject: (id, comment) => request(`/requests/${id}/reject`, {
      method: 'PATCH',
      body: JSON.stringify({ comment }),
    }),
  },
  rules: {
    revise: (id, data) => request(`/rules/${id}`, { method: 'PATCH', body: JSON.stringify(data) }),
    all: () => request('/rules'),
    create: (data) => request('/rules', { method: 'POST', body: JSON.stringify(data) }),
  },
  drafts: {
    get: (userId) => request(`/users/${userId}/request-draft`),
    save: (userId, data) => request(`/users/${userId}/request-draft`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
    remove: (userId) => request(`/users/${userId}/request-draft`, { method: 'DELETE' }),
  },
}
