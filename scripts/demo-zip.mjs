import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../', import.meta.url))

function crc32(bytes) {
  let value = 0xffffffff
  for (const byte of bytes) {
    value ^= byte
    for (let bit = 0; bit < 8; bit++) value = (value >>> 1) ^ (value & 1 ? 0xedb88320 : 0)
  }
  return (value ^ 0xffffffff) >>> 0
}

// A deterministic, stored-entry ZIP; no platform-specific zip executable needed.
export async function generateDemoZip() {
  const local = [], directory = []
  let offset = 0
  for (const filename of ['app.jsx', 'cart.js', 'cart.test.js', 'package.json']) {
    const name = Buffer.from(filename)
    const data = await readFile(resolve(root, 'backend/src/main/resources/demo', filename))
    const crc = crc32(data)
    const header = Buffer.alloc(30)
    header.writeUInt32LE(0x04034b50, 0)
    header.writeUInt16LE(20, 4)
    header.writeUInt16LE(0x21, 12) // January 1, 1980, independent of machine clock.
    header.writeUInt32LE(crc, 14)
    header.writeUInt32LE(data.length, 18)
    header.writeUInt32LE(data.length, 22)
    header.writeUInt16LE(name.length, 26)
    local.push(header, name, data)
    const central = Buffer.alloc(46)
    central.writeUInt32LE(0x02014b50, 0)
    central.writeUInt16LE(20, 4)
    central.writeUInt16LE(20, 6)
    central.writeUInt16LE(0x21, 14)
    central.writeUInt32LE(crc, 16)
    central.writeUInt32LE(data.length, 20)
    central.writeUInt32LE(data.length, 24)
    central.writeUInt16LE(name.length, 28)
    central.writeUInt32LE(offset, 42)
    directory.push(central, name)
    offset += header.length + name.length + data.length
  }
  const end = Buffer.alloc(22)
  end.writeUInt32LE(0x06054b50, 0)
  end.writeUInt16LE(4, 8)
  end.writeUInt16LE(4, 10)
  end.writeUInt32LE(directory.reduce((sum, part) => sum + part.length, 0), 12)
  end.writeUInt32LE(offset, 16)
  const zip = Buffer.concat([...local, ...directory, end])
  for (const directory of ['examples', 'frontend/public/demo']) {
    await mkdir(resolve(root, directory), { recursive: true })
    await writeFile(resolve(root, directory, 'broken-react.zip'), zip)
  }
  return zip
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  await generateDemoZip()
  console.log('Generated examples/broken-react.zip and frontend/public/demo/broken-react.zip')
}
