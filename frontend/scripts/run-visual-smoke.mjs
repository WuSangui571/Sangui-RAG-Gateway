import { spawn } from 'node:child_process'
import { join } from 'node:path'
import { createServer } from 'vite'

const server = await createServer({
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
  },
})

let childProcess = null
let closing = false

async function closeServer(exitCode) {
  if (closing) return
  closing = true

  if (childProcess && childProcess.exitCode === null) {
    childProcess.kill()
  }

  await server.close()
  process.exit(exitCode)
}

process.on('SIGINT', () => {
  void closeServer(130)
})

process.on('SIGTERM', () => {
  void closeServer(143)
})

await server.listen()
server.printUrls()

const playwrightCommand = process.platform === 'win32'
  ? process.env.ComSpec ?? 'cmd.exe'
  : join('node_modules', '.bin', 'playwright')

const playwrightArgs = process.platform === 'win32'
  ? ['/c', join('node_modules', '.bin', 'playwright.cmd'), 'test']
  : ['test']

childProcess = spawn(playwrightCommand, playwrightArgs, {
  stdio: 'inherit',
  shell: false,
})

childProcess.on('exit', (code) => {
  void closeServer(code ?? 1)
})
