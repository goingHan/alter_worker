// #AI-CODE-FILE-dev_release/v0.0.1-20260901: centralize token storage/validation (expiry-aware)
const TOKEN_KEY = 'alertworker-token'
const EXPIRY_KEY = 'alertworker-token-expiry'
const USER_KEY = 'alertworker-user'

export function getToken () {
  return localStorage.getItem(TOKEN_KEY)
}

export function getUsername () {
  return localStorage.getItem(USER_KEY) || 'admin'
}

export function setAuth (token, username, expiresInSeconds) {
  localStorage.setItem(TOKEN_KEY, token)
  if (username) localStorage.setItem(USER_KEY, username)
  if (expiresInSeconds) {
    localStorage.setItem(EXPIRY_KEY, String(Date.now() + expiresInSeconds * 1000))
  } else {
    localStorage.removeItem(EXPIRY_KEY)
  }
}

export function clearAuth () {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(EXPIRY_KEY)
  localStorage.removeItem(USER_KEY)
}

export function isAuthenticated () {
  const token = getToken()
  if (!token) return false
  const expiry = localStorage.getItem(EXPIRY_KEY)
  if (expiry && Date.now() >= Number(expiry)) return false
  return true
}
