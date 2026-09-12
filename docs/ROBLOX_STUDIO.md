# Integração futura com Roblox Studio

## Política (importante)

O Agus VR **não modifica, não distribui e não interfere** no APK oficial
do Roblox. Toda a integração proposta acontece por **exportação de dados**
do Agus VR para experiências **próprias** criadas por você no Roblox
Studio. A constante `DeviceProfile.ROBLOX_POLICY = "read-only-integration"`
formaliza isso no código.

## O que o Agus VR fornece

A ponte (app **Sistema → Ligar ponte Roblox**) transmite em tempo real:

- posição/rotação da cabeça (3DoF),
- posição da palma de cada mão,
- rotação/direção do indicador,
- os 21 keypoints de cada mão,
- estado do indicador (`extended`),
- gesto atual (`Apontando`, `Pinch`, `Punho`, `Mão aberta`, `Dois dedos`),
- eventos de seleção (`select`), grab (`grab`) e release (`release`).

Formato completo em `docs/PROTOCOL.md`.

## Arquitetura da integração

```
[Celular: Agus VR]  --TCP JSON 30Hz-->  [PC: relay_example.py]  --HTTPS-->  [Roblox Studio: HttpService]
```

O Roblox **não fala TCP** e o `HttpService` exige HTTPS; por isso existe
o relay. Para testes sem rede, há o modo **REPLAY** (sessão gravada).

## Passo a passo — sistema de teste no Studio

### Opção A: replay de sessão gravada (recomendado, sem rede)

1. No Agus VR: **Sistema → Ligar ponte Roblox**.
2. No PC (mesma rede): `nc <ip-do-celular> 28097 > sessao.jsonl`
   (ou `python3 roblox/relay_example.py <ip> --save sessao.jsonl`).
3. Gesticule no Agus VR por alguns segundos e encerre a gravação.
4. No Roblox Studio, crie uma experiência nova:
   - adicione um **Script** em `ServerScriptService` com o conteúdo de
     `roblox/AgusVRBridge.server.lua`;
   - adicione um **ModuleScript** filho dele chamado `AgusPacketReplay`
     com o conteúdo de `roblox/AgusPacketReplay.lua`;
   - cole o conteúdo de `sessao.jsonl` na variável `REPLAY` do módulo.
5. Dê **Play**. Você verá o rig (cabeça + mãos + dedos + ray do
   indicador) reproduzindo exatamente a sessão, com flashes ao receber
   eventos de seleção.

### Opção B: ao vivo via relay HTTPS

1. `python3 roblox/relay_example.py <ip-do-celular> --port 8080`
2. Exponha com túnel HTTPS: `ngrok http 8080`.
3. No script `AgusVRBridge.server.lua`: `RELAY_URL = "https://SEU_TUNEL/packet"`
   e `USE_REPLAY = false`.
4. Play no Studio — o rig acompanha suas mãos em ~30 Hz.

## Como sua experiência pode reagir

O `AgusVRBridge` centraliza tudo em `applyPacket(pkt)`. Para criar lógica
de jogo, intercepte os campos:

```lua
-- exemplo: teleportar um objeto quando o usuário apontar e selecionar
for _, ev in ipairs(pkt.events or {}) do
    if ev.type == "select" and ev.target == "cube_a" then
        -- sua lógica aqui
    end
end
```

Mapeamento sugerido para mecânicas:

| Dado Agus | Uso no Roblox |
|---|---|
| `hands[].index.tip/dir` + `extended` | raycast `workspace:Raycast(tip, dir * 10)` |
| `gesture == "Pinch"` | clique alternativo |
| `gesture == "Punho"` | agarrar objeto |
| `gesture == "Mão aberta"` | soltar |
| `events[].type == "select"` | confirmação de UI |
| `head.rot` | orientação de câmera/olhar |

## Limitações honestas

- Sem tracking posicional da cabeça no celular (`head.pos` fixo) —
  headset de celular é 3DoF.
- O replay depende do tempo real de gravação; frames perdidos na rede
  aparecem como hiatos.
- A latência do túnel HTTPS público adiciona dezenas a centenas de ms;
  para desenvolvimento prefira replay ou rede local + túnel.
