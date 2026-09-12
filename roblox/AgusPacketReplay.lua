--[[
	AgusPacketReplay (ModuleScript)
	===============================
	Reproduz uma sessão GRAVADA da ponte TCP do Agus VR dentro do Studio.

	Como gravar uma sessão:
	  1. No PC, aponte um cliente TCP para o celular:
	       nc <ip-do-celular> 28097 > sessao.jsonl
	     (ou use o roblox/relay_example.py que também salva em arquivo)
	  2. Abra sessao.jsonl, copie as linhas e cole na string REPLAY abaixo
	     (uma linha JSON por frame, exatamente como recebido).

	O módulo decodifica cada linha com HttpService:JSONDecode e entrega
	os pacotes na ordem original (30 Hz por padrão).
]]

local HttpService = game:GetService("HttpService")

local Replay = {}

-- Cole aqui o conteúdo gravado (uma linha JSON por frame):
local REPLAY = [[
]]

local packets = nil

local function parse()
	packets = {}
	for line in REPLAY:gmatch("[^\r\n]+") do
		if line:sub(1, 1) == "{" then
			local ok, pkt = pcall(function()
				return HttpService:JSONDecode(line)
			end)
			if ok then table.insert(packets, pkt) end
		end
	end
	return packets
end

function Replay.count()
	if not packets then parse() end
	return #packets
end

function Replay.iter(loop)
	if not packets then parse() end
	local i = 0
	return function()
		if #packets == 0 then return nil end
		i = i + 1
		if i > #packets then
			if loop then i = 1 else return nil end
		end
		return packets[i]
	end
end

return Replay
