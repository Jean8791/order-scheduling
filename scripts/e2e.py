import json, time, urllib.request, urllib.error, concurrent.futures as cf
import os
P=os.environ.get("PEDIDO_URL","http://localhost:8081"); A=os.environ.get("AGENDAMENTO_URL","http://localhost:8082")
def req(m,u,b=None):
    r=urllib.request.Request(u,method=m,data=json.dumps(b).encode() if b is not None else None,headers={"Content-Type":"application/json"})
    try:
        with urllib.request.urlopen(r,timeout=10) as x: return x.status, json.loads(x.read() or "null")
    except urllib.error.HTTPError as e: return e.code, json.loads(e.read() or "null")
def pedido(uf,data,cli):
    return {"cliente":cli,"endereco":{"logradouro":"Rua A","numero":"1","cidade":"X","uf":uf,"cep":"01310100"},
            "itens":[{"sku":"CAFE-500G","descricao":"Café","quantidade":2}],"dataEntregaDesejada":data}
def aguardar(pid):
    for _ in range(60):
        s,b=req("GET",f"{P}/pedidos/{pid}")
        if b["status"]!="AGUARDANDO_AGENDAMENTO": return b
        time.sleep(0.5)
    return b
ok=0; falhas=0
def check(nome,cond,extra=""):
    global ok,falhas
    ok+=cond; falhas+=(not cond); print(("OK   " if cond else "FAIL ")+nome+(f"  [{extra}]" if extra else ""))
D="2026-12-10"
s,m=req("POST",f"{A}/janelas",{"uf":"SP","data":D,"periodo":"MANHA","capacidade":1}); check("janela SP manhã criada (201)",s==201)
s,t=req("POST",f"{A}/janelas",{"uf":"SP","data":D,"periodo":"TARDE","capacidade":1}); check("janela SP tarde criada (201)",s==201)
s,_=req("POST",f"{A}/janelas",{"uf":"SP","data":D,"periodo":"MANHA","capacidade":5}); check("janela duplicada → 409",s==409,s)
s,a=req("POST",f"{P}/pedidos",pedido("SP",D,"Cliente A")); check("pedido A criado (201)",s==201)
a=aguardar(a["id"]); check("pedido A AGENDADO na manhã",a["status"]=="AGENDADO" and a["agendamento"]["periodo"]=="MANHA",a["status"])
s,b=req("POST",f"{P}/pedidos",pedido("SP",D,"Cliente B")); b=aguardar(b["id"])
check("pedido B AGENDADO na tarde",b["status"]=="AGENDADO" and b["agendamento"]["periodo"]=="TARDE",b["status"])
s,c=req("POST",f"{P}/pedidos",pedido("SP",D,"Cliente C")); c=aguardar(c["id"])
check("pedido C SEM_AGENDA (janelas lotadas)",c["status"]=="SEM_AGENDA",c.get("motivo"))
s,_=req("POST",f"{P}/pedidos/{a['id']}/cancelar",{"motivo":"Cliente desistiu"}); check("cancelar A (200)",s==200)
for _ in range(40):
    _,js=req("GET",f"{A}/janelas?uf=SP&de={D}&ate={D}")
    manha=[j for j in js if j["periodo"]=="MANHA"][0]
    if manha["ocupados"]==0: break
    time.sleep(0.5)
check("vaga da manhã liberada após cancelar A",manha["ocupados"]==0,manha)
_,ag=req("GET",f"{A}/agendamentos/{a['id']}"); check("agendamento de A CANCELADO",ag["status"]=="CANCELADO")
s,d=req("POST",f"{P}/pedidos",pedido("SP",D,"Cliente D")); d=aguardar(d["id"])
check("pedido D reaproveita a vaga liberada (manhã)",d["status"]=="AGENDADO" and d["agendamento"]["periodo"]=="MANHA",d["status"])
s,_=req("POST",f"{P}/pedidos/{a['id']}/cancelar",{"motivo":"de novo"}); check("cancelar A de novo → 422",s==422,s)
req("POST",f"{A}/janelas",{"uf":"RJ","data":D,"periodo":"MANHA","capacidade":2})
with cf.ThreadPoolExecutor(10) as ex:
    ids=list(ex.map(lambda i: req("POST",f"{P}/pedidos",pedido("RJ",D,f"Concorrente {i}"))[1]["id"], range(10)))
fim=[aguardar(i) for i in ids]
agendados=sum(f["status"]=="AGENDADO" for f in fim); sem=sum(f["status"]=="SEM_AGENDA" for f in fim)
_,rj=req("GET",f"{A}/janelas?uf=RJ&de={D}&ate={D}")
check("concorrência: 10 pedidos para 2 vagas → 2 agendados, 8 sem agenda",agendados==2 and sem==8,f"agendados={agendados} sem_agenda={sem}")
check("concorrência: janela RJ não estourou (ocupados=2)",rj[0]["ocupados"]==2,rj[0]["ocupados"])
s,e=req("POST",f"{P}/pedidos",{"cliente":"","endereco":{"logradouro":"R","numero":"1","cidade":"X","uf":"sp","cep":"1"},"itens":[],"dataEntregaDesejada":"2000-01-01"})
check("pedido inválido → 400 com campos",s==400 and len(e.get("campos",{}))>=4,sorted(e.get("campos",{})))
s,_=req("GET",f"{P}/pedidos/nao-existe"); check("pedido inexistente → 404",s==404)
s,lst=req("GET",f"{P}/pedidos?status=AGENDADO"); check("listar por status AGENDADO",s==200 and lst["totalElements"]==4,lst.get("totalElements"))
print(f"\nResultado: {ok} OK, {falhas} falha(s)")
