# Agus VR

![Android Build](https://github.com/neresbielxx2-ai/Agus-VR-/actions/workflows/android-build.yml/badge.svg)

Sistema de realidade virtual para Android com **hand tracking real**,
interação espacial e UI própria — construído do zero, sem depender de SDK
proprietário de headset. O celular vira o headset (modo side-by-side
estéreo) e a câmera alimenta o rastreamento das mãos via **MediaPipe**.

> O objetivo deste projeto é **funcionamento real**: onde uma tecnologia
> não existe no Android comum (ex.: depth de mãos sem sensor dedicado),
> a limitação está documentada e a alternativa mais viável foi
> implementada — nada de animações fingindo tracking.

---

## Índice

1. [Como rodar](#como-rodar)
2. [Arquitetura](#arquitetura)
3. [Módulos](#módulos)
4. [Hand Tracking](#hand-tracking)
5. [Agus Point Interaction](#agus-point-interaction)
6. [Seleção por aproximação](#seleção-por-aproximação)
7. [Gestos disponíveis](#gestos-disponíveis)
8. [Interface VR](#interface-vr)
9. [Agus Hand Lab](#agus-hand-lab)
10. [Recursos inovadores](#recursos-inovadores)
11. [Performance](#performance)
12. [Formato dos dados / API de input](#formato-dos-dados--api-de-input)
13. [Como criar novos objetos interativos](#como-criar-novos-objetos-interativos)
14. [Como criar novos aplicativos](#como-criar-novos-aplicativos)
15. [Como reproduzir o Agus Hand Lab](#como-reproduzir-o-agus-hand-lab)
16. [Integração futura com Roblox Studio](#integração-futura-com-roblox-studio)
17. [Eventos e frequências](#eventos-e-frequências)
18. [Limitações do Android](#limitações-do-android)
19. [Status: implementado / em desenvolvimento / planejado](#status)

---

## Como rodar

### Requisitos

- Android Studio (Hedgehog+) com JDK 17
- Android 8.0+ (API 26), GLES 3.0
- ~8 MB livres para o modelo de hand tracking (baixado no primeiro uso)

### Build

```bash
git clone <repo> Agus-VR-
cd Agus-VR-
# o wrapper jar não é versionado; gere uma vez se for usar linha de comando:
gradle wrapper --gradle-version 8.4   # ou abra direto no Android Studio
./gradlew :app:installDebug
```

### Build automático (GitHub Actions)

Toda push em `main` / `arena/01a09621-agus-vr` compila o app na nuvem
(`.github/workflows/android-build.yml`): JDK 17 + Gradle 8.4 + SDK 34,
gerando **AgusVR-debug.apk** (instalável direto) e o release sem assinatura
como artefatos da aba **Actions → Android Build**. Para disparar
manualmente: Actions → Android Build → *Run workflow*.

### Primeiro uso

1. Abra o **Agus VR** (tela de configuração).
2. Toque em **Baixar modelo** (modelo `hand_landmarker.task` do MediaPipe,
   ~8 MB, fica no armazenamento interno).
3. Toque em **Permitir câmera**.
4. **Iniciar Motor VR** → coloque o celular num headset tipo Cardboard
   (paisagem) → faça o gesto de *recentralizar* (toque duplo na lateral)
   quando quiser definir o "frente".

Sem modelo ou sem câmera o app continua funcionando com **fallback
real**: seleção por gaze + toque, com o estado mostrado na tela — o
sistema nunca finge que está rastreando mãos.

---

## Arquitetura

```
┌────────────────────────────────────────────────────────────────┐
│                        Agus VR Runtime                         │
│   VrActivity · VrEngine (tick) · AgusRenderer (estéreo SBS)    │
└──────┬───────────────┬──────────────────┬──────────────┬───────┘
       │               │                  │              │
┌──────▼─────┐  ┌──────▼──────┐  ┌────────▼───────┐ ┌────▼────────┐
│ Agus Input │  │ Agus Hand   │  │ Agus Point     │ │ Agus UI     │
│ HeadTrack  │  │ Tracking    │  │ Interaction    │ │ Painéis/    │
│ Touch      │  │ MediaPipe+  │  │ ray + círculo  │ │ HUD/Toast   │
└──────┬─────┘  │ CameraX     │  └────────┬───────┘ └────┬────────┘
       │        └──────┬──────┘           │              │
       │               ▼                  ▼              ▼
       │        ┌─────────────────────────────────────────────┐
       │        │            Agus Interaction                 │
       │        │  hover · seleção por aproximação · grab     │
       │        └──────────────────┬──────────────────────────┘
       │                           ▼
       │        ┌──────────────────────────────────────────────┐
       │        │            Agus Applications                 │
       │        │  HandLab · Biblioteca · Notas · Performance… │
       │        └──────────────────┬───────────────────────────┘
       │                           ▼
       │        ┌──────────────────────────────────────────────┐
       │        │            Agus Spatial System               │
       │        │  notas espaciais · workspace · smart objects │
       │        └──────────────────────────────────────────────┘
       ▼
┌──────────────┐  ┌───────────────┐  ┌──────────────────────────┐
│ Agus         │  │ Agus Storage  │  │ Agus Compatibility Layer │
│ Performance  │  │ settings/uso/ │  │ DeviceProfile + ponte    │
│ FPS/térmica  │  │ notas/atalhos │  │ Roblox (só exportação)   │
└──────────────┘  └───────────────┘  └──────────────────────────┘
```

Fluxo de um frame (GL thread, a cada vsync limitado pelo cap de FPS):

1. `HeadTracker.update()` — orientação 3DoF (sensor de rotação).
2. `HandTracker` publica `HandFrame` (detectado em thread própria).
3. `PointInteraction.update()` — gesto POINT vira ray + círculo.
4. `InteractionManager.update()` — hover, debounce, aproximação.
5. Apps/UI atualizam; `AdaptiveQuality` ajusta escala/FPS.
6. `AgusRenderer` desenha 2 olhos (FBO quando há escala de resolução).

---

## Módulos

| Módulo | Pacote | Responsabilidade |
|---|---|---|
| **Agus VR Runtime** | `com.agusvr.runtime` | Engine, renderer estéreo, atividades, som, ambiente |
| **Agus Input** | `com.agusvr.input` | Head tracking (sensor de rotação), toque, recenter |
| **Agus Hand Tracking** | `com.agusvr.handtracking` | CameraX + MediaPipe, gestos, projeção 2D→3D, rig visual, download do modelo |
| **Agus Point Interaction** | `com.agusvr.point` | Ray do indicador, círculo na ponta do dedo, máquina de estados |
| **Agus Interaction** | `com.agusvr.interaction` | Interactable, hover, seleção por aproximação, grab/release |
| **Agus UI** | `com.agusvr.ui` | Painéis espaciais (canvas→textura), menu principal |
| **Agus Applications** | `com.agusvr.apps` | Registro/ciclo de vida dos apps VR internos |
| **Agus Spatial System** | `com.agusvr.spatial` | Notas espaciais, workspace persistente |
| **Agus Performance** | `com.agusvr.performance` | FPS/memória/bateria/térmica, modos, qualidade adaptativa |
| **Agus Storage** | `com.agusvr.storage` | Settings, uso (adaptive UI), notas, atalhos (JSON) |
| **Agus Compatibility Layer** | `com.agusvr.compat` | Capacidades do aparelho e fallbacks |
| **Roblox bridge** | `com.agusvr.bridge` | Exportação TCP JSON (somente dados, nunca APK) |

---

## Hand Tracking

Pipeline **real** (nada simulado):

```
CameraX ImageAnalysis (640×480, KEEP_ONLY_LATEST)
  → bitmap girado + espelhado ("visão de espelho")
  → MediaPipe HandLandmarker (VIDEO mode, 2 mãos, 21 keypoints)
  → GestureClassifier (geometria normalizada pelo tamanho da mão)
  → HandProjection (2D→3D ancorado na cabeça)
  → suavização exponencial → HandFrame publicado
```

O que é detectado por mão:

- **mão esquerda / direita** — handedness do MediaPipe (imagem sempre
  espelhada, então os rótulos saem corretos);
- **posição** — centro da palma em metros no espaço do mundo;
- **rotação/direção** — eixo punho→indicador (usado pelo ray);
- **dedos e articulações** — 21 keypoints 3D (índices padrão MediaPipe);
- **indicador** — ponta, direção unitária e estado estendido;
- **estado dos dedos** — estendido/dobrado por comparação de distâncias
  punho→ponta vs punho→PIP;
- **gestos** — ver [Gestos](#gestos-disponíveis).

### Projeção 2D → 3D (sem sensor de profundidade)

A distância da mão é estimada pelo **tamanho da mão na imagem**
(mão maior = mais perto) — isso dá resposta real de aproximação. O `z`
relativo do MediaPipe ajusta a profundidade por keypoint, e o resultado
é ancorado na orientação atual da cabeça (a mão fica "presa" ao mundo
quando você vira o rosto). Constantes calibráveis em `HandProjection`.

### Modelo

O modelo `hand_landmarker.task` (~8 MB) é **baixado no primeiro uso**
(`HandModelManager`) para manter o repositório/APK enxuto. Sem ele, o
hand tracking fica desativado e o app mostra isso com clareza.

---

## Agus Point Interaction

Sistema proprietário de apontar. Quando **somente o indicador** está
estendido:

```
☝️ ──────────────── ○
ponta do dedo        círculo (ponto de interação)
─────────────        linha/ray na direção do dedo
```

- **círculo** na ponta do dedo (anel billboard), tamanho muda com a
  distância ao alvo e **encolhe** durante a confirmação;
- **linha** discreta e futurista da ponta do dedo até o acerto
  (ou 60% do alcance, se livre);
- o ray detecta **botões, apps, objetos, menus, elementos de UI e
  objetos do Agus Hand Lab** — tudo que implementa `Interactable`;
- estados visuais do alvo: **normal → detectado → selecionando →
  abrindo** (emissivo + anel de progresso + flash).

Implementação: `com.agusvr.point.PointInteraction`.

## Seleção por aproximação

`InteractionManager` implementa o fluxo com anti clique-acidental:

1. Ray sobre o alvo por **260 ms estável** → alvo **DETECTADO**
   (debounce de entrada — passagem rápida não faz nada).
2. Palma entra na janela de distância
   `[0.04 m … selectDistance (0.35 m)]` enquanto continua apontando →
   **progresso** sobe (círculo encolhe, barra preenche no painel).
3. Sair da janela faz o progresso **decair** (não é binário).
4. Progresso completo → **seleção confirmada** → animação *abrindo*
   (380 ms) → ação executada → **cooldown de 1 s** no mesmo alvo.

Parâmetros em `InteractionManager` (constantes) e `Settings.selectDistance`.
O sistema usa **distância da mão + direção do dedo + alvo detectado**,
exatamente como pedido.

## Gestos disponíveis

| Gesto | Detecção | Ação padrão |
|---|---|---|
| Mão aberta | 4 dedos + polegar estendidos | segurar 1 s → menu (atalho) |
| Punho | todos os dedos dobrados | segurar 1 s → Focus Mode; perto de objeto → **grab** |
| Apontar (indicador) | só o indicador estendido | ativa o Agus Point Interaction |
| Pinch | polegar+índice tocando | atalho configurável (padrão: recentrar, se sem alvo) |
| Dois dedos (V) | indicador+médio | segurar 1 s → HUD de performance |
| Release | abrir a mão segurando algo | solta o objeto agarrado |

Os atalhos são **configuráveis** (`AppDataStore` → `shortcuts.json`,
ações: `menu/focus/recenter/perf/home/none`). A classificação usa
estabilização de 2 frames para evitar flicker.

---

## Interface VR

Escura, minimalista, futurista — sem estética "painel de avião":

- fundo `#05070E`, acentos ciano `#6EE7FF`, verde `#7DFFB2`;
- **cards flutuantes** grandes (alvos confortáveis para hand tracking);
- painéis com cantos arredondados, títulos com barra de acento;
- animações suaves (entrada/saída do menu por lerp, flashes de seleção);
- toast, chip de alvo e HUD ancorados na visão;
- texto nativo do Android renderizado em canvas → textura (nítido).

Menu principal: **Biblioteca · Aplicativos · Agus Hand Lab ·
Configurações · Performance · Espaço Pessoal · Sistema**.

## Agus Hand Lab

Ambiente de teste (`apps/HandLabApp.kt`):

- **modelo visual da mão** (esqueleto: 21 esferas + ossos por mão);
- **passthrough** da câmera para ver as próprias mãos;
- **cubos** A/B/C agarráveis + smart glow por proximidade;
- **painel de botões** com contadores reais (feedback de seleção);
- **smart object** (esfera que pulsa com a mão e com o ray);
- **alvo de precisão** para treinar o ray;
- **painel de gestos ao vivo** (gesto de cada mão + latência);
- cartão de instruções.

## Recursos inovadores

| Recurso | Estado | Como funciona |
|---|---|---|
| **Spatial Workspace** | ✔ | painéis/notas agarráveis (punho move, mão aberta solta); posições persistidas |
| **Gesture Shortcuts** | ✔ | mapa gesto→ação configurável e persistido |
| **Smart Objects** | ✔ | emissivo/escala reagem à distância real da palma e ao ray |
| **Spatial Notes** | ✔ | notas 3D coloridas, agarráveis, persistidas em JSON |
| **Focus Mode** | ✔ | esconde menu/HUD, reduz efeitos e aumenta fog |
| **Adaptive UI** | ✔ | menu ordenado por frequência de uso (contagem persistida) |

---

## Performance

Telemetria **real** (`PerfMonitor`): FPS/frame (EMA), memória Java +
nativa, RAM livre do sistema, carga de CPU (`/proc/stat`), bateria e
temperatura (sticky broadcast).

Modos (`PerfPolicy`): **Economia · Desempenho · Equilibrado ·
Qualidade** — controlam resolução interna, cap de FPS, efeitos e
sombras. Controles individuais: resolução (50–100%), FPS (30/45/60/72),
efeitos, sombras, hand tracking on/off.

`AdaptiveQuality`: se o FPS real cair abaixo de 78% da meta por 2,5 s
(ou bateria ≥ 42 °C), degrada em passos (efeitos → resolução → FPS);
recupera quando há folga. Emergência térmica (≥ 46 °C) força o mínimo.

Escalas de resolução usam **FBO real** (renderiza menor e faz blit),
não apenas upscale de viewport.

## Formato dos dados / API de input

### HandFrame (memória)

```
HandFrame
 ├─ left/right: HandState
 │   ├─ present, side, score, lastSeenMs
 │   ├─ gesture (enum Gesture)
 │   ├─ norm[21]  — x,y,z normalizados da câmera
 │   ├─ world[21] — posições 3D em metros (espaço do mundo)
 │   ├─ palm[3], indexTip[3], indexDir[3]
 │   ├─ pinchDistance, imageHandSize, estimatedDistance
 └─ timestamp, processingMs, flags (enabled/modelReady/cameraGranted)
```

Consumidores não assinam nada: o engine lê o último `HandFrame` a cada
tick (publicação atômica na thread de detecção). Isso mantém o loop de
render livre de bloqueios.

### Interactable (API de interação)

```kotlin
interface Interactable {
    val id: String
    var enabled: Boolean
    val hoverLabel: String; val grabbable: Boolean; val center: FloatArray
    fun rayHit(ray: Ray): Float           // distância ou -1
    fun onHoverStart(); fun onHoverEnd()
    fun onSelect(source: SelectSource)     // APPROACH | DWELL | TOUCH
    fun onReleased(); fun onGrabbed()
    fun applyState(state: TargetState, progress: Float)
}
```

### Exportação externa

Ponte TCP JSON (30 Hz) — formato completo em [`docs/PROTOCOL.md`](docs/PROTOCOL.md).

---

## Como criar novos objetos interativos

```kotlin
// 1. um item visual
val item = SceneObject(meshes.cube, SceneObject.PROG_LIT)
item.pos[0] = 0f; item.pos[1] = -0.3f; item.pos[2] = -1.5f
item.scale[0] = 0.2f; item.scale[1] = 0.2f; item.scale[2] = 0.2f
env.addItem(item)

// 2. um alvo em volta dele
val alvo = BoxTarget("meu_objeto", item, floatArrayOf(0.12f, 0.12f, 0.12f)) { src ->
    env.toast("Selecionado por $src")
}
alvo.hoverLabel = "Meu objeto"
alvo.grabbable = true
env.addInteractable(alvo)
```

Tipos de alvo: `BoxTarget` (AABB), `SphereTarget` (esfera),
`PanelTarget` (quad de UI com subelementos). Tudo registrado via
`SceneEnv` é removido automaticamente quando o app fecha.

## Como criar novos aplicativos

```kotlin
class MeuApp : AgusApp {
    override val id = "meu_app"
    override val name = "Meu App"
    override fun build(env: SceneEnv) {
        val painel = VrPanel("meu_painel", 1.6f, 1.0f, env.meshes, 900)
        painel.title = "Meu App"
        painel.addButton("ok", "OK", -0.2f, -0.1f, 0.4f, 0.2f) { _ ->
            env.toast("Olá!")
        }
        painel.place(0f, 0f, -2f, 0f)
        env.addPanel(painel)
    }
    override fun update(dtMs: Float) { /* animações */ }
}
```

Depois registre o factory em `AppManager.create()` e (opcional) inclua
o id no menu/estantes. O `SceneEnv` expõe: settings, storage, toast,
head/hands, launch de outros apps, passthrough, recenter.

## Como reproduzir o Agus Hand Lab

O Lab é 100% construído pela API pública descrita acima — leia
`app/src/main/java/com/agusvr/apps/HandLabApp.kt`:

1. `env.setPassthrough(true)` para ver as mãos;
2. crie cubos `BoxTarget` (grabbable) + sombras blob;
3. crie o painel de gestos com `customDraw` lendo `hands().frame`;
4. crie botões com contadores (`PanelElement.onSelect`);
5. crie smart objects (esfera + `SphereTarget`);
6. no `update()`, leia palma/gestos para glow e flutuação;
7. `dispose()` → `env.setPassthrough(false)`.

---

## Integração futura com Roblox Studio

**Política:** o APK oficial do Roblox nunca é modificado, distribuído ou
alterado (`DeviceProfile.ROBLOX_POLICY = "read-only-integration"`).

A integração exporta dados para experiências **próprias** no Studio:

- ponte TCP JSON 30 Hz no celular (app Sistema);
- `roblox/AgusVRBridge.server.lua` — rig de teste (cabeça, mãos, dedos,
  ray, eventos) dentro do Studio;
- `roblox/AgusPacketReplay.lua` — replay de sessões gravadas (sem rede);
- `roblox/relay_example.py` — relay TCP→HTTPS para modo ao vivo.

Dados exportados: posição da cabeça, posição das mãos, direção/rotação
do indicador, 21 keypoints por mão, estado do indicador, gesto de
apontar, pinch, grab/release e eventos de seleção.

Guia completo: [`docs/ROBLOX_STUDIO.md`](docs/ROBLOX_STUDIO.md) ·
Protocolo: [`docs/PROTOCOL.md`](docs/PROTOCOL.md)

## Eventos e frequências

| Evento | Origem | Destino |
|---|---|---|
| `select` | InteractionManager (aproximação/toque) | app alvo, som, ponte |
| `grab` / `release` | GrabController | app alvo, som, ponte |
| hover start/end | InteractionManager | destaque visual, som |
| gesto reconhecido | GestureClassifier | atalhos, Point Interaction |
| frame de mãos | HandTracker (thread própria) | HandFrame (leitura por tick) |

| Ciclo | Frequência |
|---|---|
| Render | cap de 30/45/60/72 fps |
| Detecção de mãos | 30 Hz (15 Hz no modo Economia) |
| Sensor de rotação | SENSOR_DELAY_GAME (~120 Hz) |
| Ponte TCP | 30 Hz |
| Amostragem lenta (CPU/bateria) | 2 Hz |

---

## Limitações do Android

Documentadas com honestidade (e com fallback):

1. **Sem tracking posicional da cabeça** — celulares comuns são 3DoF
   (rotação). `head.pos` é fixo; documentado no protocolo.
2. **Profundidade das mãos estimada** (tamanho na imagem + z relativo),
   não medida. Precisão suficiente para seleção por aproximação, não
   para cirurgia. Constantes em `HandProjection`.
3. **Câmera no headset**: o passthrough depende de a lente ficar
   desobstruída no seu headset; com lente tampada o Lab mostra só o
   esqueleto 3D.
4. **MediaPipe em aparelhos fracos** pode custar 30–60 ms/frame — o
   modo Economia reduz para 15 Hz e o AdaptiveQuality reage.
5. **Sem OpenXR universal** em Android comum; o `DeviceProfile` reporta
   o que existe de fato (feature `vr.headtracking`, giroscópio etc.).
6. **Sem câmera** (ou permissão negada) → fallback gaze+toque, com
   aviso claro na UI.
7. **Temperatura**: não existe API térmica universal antes do Android
   10/12; usamos temperatura de bateria + `ThermalManager` quando
   disponível.

## Status

### ✔ Implementado

- Motor VR estéreo SBS próprio (GLES 3.0) com escala de resolução via FBO
- Head tracking 3DoF real (sensor de rotação) + recenter + mounts
- Hand tracking real (MediaPipe, 2 mãos, 21 keypoints) com download do modelo
- 6 gestos com estabilização + atalhos configuráveis
- Agus Point Interaction (círculo + ray + estados visuais)
- Seleção por aproximação com debounce, janela de distância e cooldown
- Grab/release de objetos e painéis (spatial workspace)
- Agus Hand Lab completo (passthrough, cubos, botões, smart objects, alvo)
- Menu espacial com cards + Adaptive UI por uso
- Apps: Biblioteca, Aplicativos, Configurações, Performance, Espaço
  Pessoal, Sistema, Notas Espaciais
- Spatial Notes persistidas e agarráveis
- Focus Mode, Smart Objects, Gesture Shortcuts
- Telemetria real (FPS, memória, bateria, temperatura, CPU) + AdaptiveQuality
- 4 modos de desempenho + controles finos persistidos
- Ponte TCP JSON 30 Hz + scripts Roblox Studio + relay
- Fallback gaze+toque quando não há câmera/modelo

### 🔧 Em desenvolvimento

- Seleção por dwell opcional (parâmetros prontos, UX em ajuste)
- Recenter automático ao detectar drift
- Teclado virtual espacial para notas com texto livre
- Curvatura dos painéis do menu (ergonomia)

### 📅 Planejado

- Suporte a headset com 6DoF quando disponível no dispositivo
- OpenXR onde existir (camada de compatibilidade já isola isso)
- Gravação/replay de sessões dentro do próprio app
- Experiências de terceiros via manifesto declarativo
- Integração Roblox ao vivo por WebRTC/relay próprio

---

## Estrutura do repositório

```
app/src/main/java/com/agusvr/
  launcher/    → tela de configuração 2D (antes do VR)
  runtime/     → engine, renderer, atividade, ambiente, som
  runtime/gl/  → shaders, meshes, cena, FBO
  input/       → head tracking, toque
  handtracking/→ MediaPipe, gestos, projeção, rig, modelo
  point/       → Agus Point Interaction
  interaction/ → alvos, hover, seleção, grab
  ui/          → painéis, menu
  apps/        → aplicativos VR
  spatial/     → notas/workspace
  performance/ → monitor, políticas, adaptativo
  storage/     → persistência JSON
  compat/      → device profile
  bridge/      → ponte TCP (Roblox)
roblox/        → scripts Luau + relay python
docs/          → protocolo e guia Roblox Studio
```

Agus VR — runtime espacial próprio, hand tracking real, pronto para crescer.
