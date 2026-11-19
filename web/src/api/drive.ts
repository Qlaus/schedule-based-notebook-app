import { googleFetch, googleJson, HttpError } from './http'

/** Google Drive REST v3 restricted to the hidden appDataFolder (scope drive.appdata). */
export interface DriveFile { id: string; name?: string; appProperties?: Record<string, string> }

export async function listAppData(): Promise<DriveFile[]> {
  const out: DriveFile[] = []
  let page: string | undefined
  do {
    const q = new URLSearchParams({
      spaces: 'appDataFolder',
      fields: 'nextPageToken,files(id,name,appProperties,modifiedTime)',
      pageSize: '1000',
    })
    if (page) q.set('pageToken', page)
    const res = await googleJson<{ files?: DriveFile[]; nextPageToken?: string }>(`https://www.googleapis.com/drive/v3/files?${q}`)
    out.push(...(res.files ?? []))
    page = res.nextPageToken
  } while (page)
  return out
}

export async function downloadText(id: string): Promise<string> {
  const res = await googleFetch(`https://www.googleapis.com/drive/v3/files/${encodeURIComponent(id)}?alt=media`)
  if (!res.ok) throw new HttpError(res.status, `HTTP ${res.status}`)
  return res.text()
}

/** Creates (no id) or updates a file with a multipart/related upload: metadata + content. */
export async function uploadFile(
  existingId: string | null,
  name: string,
  appProperties: Record<string, string>,
  content: string,
  contentType: string,
): Promise<DriveFile> {
  const boundary = 'calnotes' + crypto.randomUUID().replace(/-/g, '')
  const metadata = existingId ? { appProperties } : { name, parents: ['appDataFolder'], appProperties }
  const body =
    `--${boundary}\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n${JSON.stringify(metadata)}\r\n` +
    `--${boundary}\r\nContent-Type: ${contentType}\r\n\r\n${content}\r\n--${boundary}--\r\n`
  const url = existingId
    ? `https://www.googleapis.com/upload/drive/v3/files/${encodeURIComponent(existingId)}?uploadType=multipart&fields=id,name,appProperties`
    : 'https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,appProperties'
  return googleJson<DriveFile>(url, {
    method: existingId ? 'PATCH' : 'POST',
    headers: { 'Content-Type': `multipart/related; boundary=${boundary}` },
    body,
  })
}
