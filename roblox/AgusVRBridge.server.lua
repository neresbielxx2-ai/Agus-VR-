--[[
	AgusVRBridge.server.lua
	=======================
	Receptor/reprodutor do protocolo Agus VR dentro do Roblox Studio.

	O Agus VR NUNCA modifica o Roblox. Este script vive em uma experiência
	PRÓPRIA sua e apenas consome dados exportados pelo Agus VR:

	  Modo A (REPLAY — recomendado para testes):
	    Cole um JSONL gravado da ponte TCP em AgusPacketReplay (module)
	    e o script reproduz cabeça + mãos + gestos + eventos frame a frame.

	  Modo B (AO VIVO, requer relay HTTPS):
	    Configure RELAY_URL com o endpoint HTTPS do relay (ver
	    roblox/relay_example.py + docs/ROBLOX_STUDIO.md).

	Coloque este Script em ServerScriptService e o ModuleScript
	AgusPacketReplay como filho dele (ou ajuste o require abaixo).
]]

local HttpService = game:GetService("HttpService")
local RunService = game:GetService("RunService")

local RELAY_URL = ""            -- ex.: "https://SEU_TUNEL/packet"
local POLL_HZ = 30
local USE_REPLAY = true         -- true = reproduz sessão gravada

-- ---------------------------------------------------------------- rig
local rigRoot = Instance.new("Model")
rigRoot.Name = "AgusVRRig"
rigRoot.Parent = workspace

local function makePart(name, size, color)
	local p = Instance.new("Part")
	p.Name = name
	p.Size = size
	p.Color = color
	p.Anchored = true
	p.CanCollide = false
	p.Material = Enum.Material.Neon
	p.Parent = rigRoot
	return p
end

local headPart = makePart("Head", Vector3.new(0.5, 0.5, 0.5), Color3.fromRGB(110, 231, 255))
headPart.Position = Vector3.new(0, 6, 0)

local hands = {}
for _, side in ipairs({ "L", "R" }) do
	local color = side == "L" and Color3.fromRGB(110, 231, 255) or Color3.fromRGB(190, 140, 255)
	local palm = makePart("Palm_" .. side, Vector3.new(0.22, 0.08, 0.28), color)
	local idx = makePart("IndexTip_" .. side, Vector3.new(0.1, 0.1, 0.1), Color3.fromRGB(255, 255, 255))
	local ray = makePart("Ray_" .. side, Vector3.new(0.02, 0.02, 2), color)
	ray.Transparency = 0.5

	local fingers = {}
	for i = 1, 21 do
		local f = makePart("J" .. side .. "_" .. i, Vector3.new(0.05, 0.05, 0.05), color)
		f.Shape = Enum.PartType.Ball
		f.Transparency = 0.25
		table.insert(fingers, f)
	end

	hands[side] = { palm = palm, index = idx, ray = ray, fingers = fingers }
end

local origin = Vector3.new(0, 5, 0)
local SCALE = 1 -- metros → studs

local function v3(arr, fallback)
	if type(arr) ~= "table" then return fallback end
	return origin + Vector3.new(arr[1] or 0, arr[2] or 0, arr[3] or 0) * SCALE
end

-- ---------------------------------------------------------- aplicação
local function applyPacket(pkt)
	if type(pkt) ~= "table" then return end

	-- Cabeça (posição fixa + rotação vinda do quatérnion)
	local rot = pkt.head and pkt.head.rot
	if rot then
		local w, x, y, z = rot[1], rot[2], rot[3], rot[4]
		headPart.CFrame = CFrame.new(origin) * CFrame.new(0, 0.2, 0)
			* CFrame.fromOrientation(
				-- conversão quatérnion → ângulos simples para visualização
				math.atan2(2 * (w * y - z * x), 1 - 2 * (y * y)),
				math.asin(math.clamp(2 * (w * x + y * z), -1, 1)),
				0
			)
	end

	for _, side in ipairs({ "L", "R" }) do
		local h
		for _, cand in ipairs(pkt.hands or {}) do
			if cand.side == side then h = cand break end
		end
		local set = hands[side]
		if not h or not h.present then
			set.palm.Transparency = 1
			set.index.Transparency = 1
			set.ray.Transparency = 1
			for _, f in ipairs(set.fingers) do f.Transparency = 1 end
		else
			set.palm.Transparency = 0
			set.palm.Position = v3(h.palm, origin)

			if h.landmarks then
				for i = 1, math.min(21, #h.landmarks) do
					set.fingers[i].Transparency = 0.25
					set.fingers[i].Position = v3(h.landmarks[i], origin)
				end
			end

			local idxData = h.index
			if idxData and idxData.tip then
				set.index.Transparency = 0
				local tip = v3(idxData.tip, origin)
				set.index.Position = tip
				if idxData.dir and idxData.extended then
					local dir = Vector3.new(idxData.dir[1], idxData.dir[2], idxData.dir[3])
					if dir.Magnitude > 0.01 then
						set.ray.Transparency = 0.5
						set.ray.Size = Vector3.new(0.02, 0.02, 3)
						set.ray.CFrame = CFrame.lookAt(tip, tip + dir) * CFrame.new(0, 0, -1.5)
					else
						set.ray.Transparency = 1
					end
				else
					set.ray.Transparency = 1
				end
			end

			if h.gesture and h.gesture ~= "—" then
				set.palm.Name = "Palm_" .. side .. "_" .. h.gesture
			end
		end
	end

	-- Eventos de seleção/grab/release
	for _, ev in ipairs(pkt.events or {}) do
		print(("[AgusVR] evento %s → %s"):format(ev.type, tostring(ev.target)))
		-- feedback visual simples: flash na cabeça
		task.spawn(function()
			headPart.Color = Color3.fromRGB(125, 255, 178)
			task.wait(0.15)
			headPart.Color = Color3.fromRGB(110, 231, 255)
		end)
	end
end

-- --------------------------------------------------------------- loop
if USE_REPLAY then
	local Replay = require(script:FindFirstChild("AgusPacketReplay"))
	if Replay then
		task.spawn(function()
			print("[AgusVR] modo REPLAY ativo")
			for _, pkt in Replay.iter() do
				applyPacket(pkt)
				task.wait(1 / (pkt.hz or POLL_HZ))
			end
			print("[AgusVR] replay terminado")
		end)
	else
		warn("[AgusVR] AgusPacketReplay não encontrado; criando demo sintética")
		task.spawn(function()
			local t = 0
			while true do
				local demo = {
					head = { rot = { 1, 0, 0, 0 } },
					hands = { {
						side = "R", present = true, gesture = "Apontando",
						palm = { math.sin(t) * 0.3, 0, -0.8 },
						index = {
							tip = { math.sin(t) * 0.3, 0.1, -1.0 },
							dir = { 0, 0, -1 }, extended = true
						},
						landmarks = {},
					} },
					events = {},
				}
				applyPacket(demo)
				t = t + 0.033
				task.wait(1 / POLL_HZ)
			end
		end)
	end
else
	task.spawn(function()
		while true do
			local ok, res = pcall(function()
				return HttpService:GetAsync(RELAY_URL)
			end)
			if ok and res then
				local ok2, pkt = pcall(function()
					return HttpService:JSONDecode(res)
				end)
				if ok2 then applyPacket(pkt) end
			end
			task.wait(1 / POLL_HZ)
		end
	end)
end

print("[AgusVR] AgusVRBridge carregado — protocolo v1")
