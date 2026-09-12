# Agus VR — Protocolo de exportação de dados (v1)

Este é o formato usado pela ponte do Agus VR (app Sistema → "Ligar ponte
Roblox") para exportar o estado do runtime em tempo real. É um protocolo
aberto: qualquer cliente pode consumir (Roblox Studio, ferramentas de
debug, gravação de sessões etc.).

## Transporte

- TCP, porta padrão **28097** (constante `AgusBridge.DEFAULT_PORT`).
- **1 pacote JSON por linha** (newline-delimited JSON).
- Frequência: **30 Hz** (configurável em `TcpBridgeServer(hz = …)`).
- Sem autenticação (uso em rede local). Não expor para a internet sem túnel seguro.

## Formato do pacote

```json
{
  "v": 1,
  "t": 1757690000123,
  "head": {
    "pos": [0, 0, 0],
    "rot": [w, x, y, z]
  },
  "hands": [
    {
      "side": "R",
      "present": true,
      "gesture": "Apontando",
      "palm": [x, y, z],
      "index": {
        "tip": [x, y, z],
        "dir": [x, y, z],
        "extended": true
      },
      "pinch": 0.18,
      "landmarks": [[x, y, z], "...21 keypoints..."]
    },
    { "side": "L", "...": "..." }
  ],
  "events": [
    { "type": "select", "target": "btn_a" }
  ],
  "perf": { "fps": 60, "mode": "Equilibrado" }
}
```

### Campos

| Campo | Descrição |
|---|---|
| `v` | Versão do protocolo (hoje: 1). |
| `t` | Timestamp em milissegundos do pacote. |
| `head.rot` | Quatérnion (w,x,y,z) da orientação da cabeça (3DoF). |
| `head.pos` | Sempre `[0,0,0]` — celulares deste perfil não têm tracking posicional real; documentado como limitação. |
| `hands[].side` | `"L"` esquerda, `"R"` direita (já considerando imagem espelhada). |
| `hands[].gesture` | `"—"` · `Mão aberta` · `Punho` · `Apontando` · `Pinch` · `Dois dedos`. |
| `hands[].palm` | Centro da palma em metros, no espaço do mundo (origem = cabeça). |
| `hands[].index` | Ponta (`tip`), direção (`dir`, unitário) e estado (`extended`) do indicador — é a base do Agus Point Interaction. |
| `hands[].pinch` | Distância polegar↔indicador normalizada pelo tamanho da mão (0 = tocando). |
| `hands[].landmarks` | 21 keypoints 3D em metros (índice padrão MediaPipe: 0 punho … 8 ponta do indicador …). |
| `events` | Eventos ocorridos desde o último pacote: `select`, `grab`, `release`. |

### Sistema de coordenadas

- Origem: cabeça do usuário no momento da sessão.
- Eixos: **X direita, Y cima, −Z frente** (mesma convenção usada pelo
  renderer e exportada ao Roblox).
- Unidades: metros.

## Frequências de atualização

| Dado | Frequência |
|---|---|
| Pacotes da ponte | 30 Hz |
| Detecção de mãos (MediaPipe) | 30 Hz (15 Hz no modo Economia) |
| Head tracking (sensor) | ~120 Hz interno, amostrado por frame |
| UI/painéis | repaint sob demanda (não por frame) |

## Gravação de sessão

```bash
nc <ip-do-celular> 28097 > sessao.jsonl
```

O arquivo resultante pode ser reproduzido no Roblox Studio com o módulo
`roblox/AgusPacketReplay.lua` — ver `docs/ROBLOX_STUDIO.md`.
