export interface AdminLoginDTO {
  username: string
  password: string
}

export interface AdminUserVO {
  id: number
  username: string
  status: string
}

export interface AdminLoginVO {
  access_token: string
  token_type: string
  expires_at: string
  user: AdminUserVO
}
