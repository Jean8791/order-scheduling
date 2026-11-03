import json, time, urllib.request, urllib.error, concurrent.futures as cf
import os, uuid
O=os.environ.get("ORDER_URL","http://localhost:8081"); S=os.environ.get("SCHEDULING_URL","http://localhost:8082"); A=os.environ.get("AUTH_URL","http://localhost:8083")
TOKEN=None
def req(method,url,body=None,auth=True):
    headers={"Content-Type":"application/json"}
    if auth and TOKEN: headers["Authorization"]=f"Bearer {TOKEN}"
    r=urllib.request.Request(url,method=method,data=json.dumps(body).encode() if body is not None else None,headers=headers)
    try:
        with urllib.request.urlopen(r,timeout=10) as x: return x.status, json.loads(x.read() or "null")
    except urllib.error.HTTPError as e: return e.code, json.loads(e.read() or "null")
def order(state,date,customer):
    return {"customer":customer,"address":{"street":"Rua A","number":"1","city":"X","state":state,"zipCode":"01310100"},
            "items":[{"sku":"CAFE-500G","description":"Café","quantity":2}],"desiredDeliveryDate":date}
def wait(order_id):
    for _ in range(60):
        s,b=req("GET",f"{O}/orders/{order_id}")
        if b["status"]!="AWAITING_SCHEDULING": return b
        time.sleep(0.5)
    return b
passed=0; failed=0
def check(name,cond,extra=""):
    global passed,failed
    passed+=cond; failed+=(not cond); print(("OK   " if cond else "FALHA")+name+(f"  [{extra}]" if extra else ""))
D=os.environ.get("E2E_DATE","2026-12-10")
EMAIL=f"e2e-{uuid.uuid4().hex[:8]}@teste.com"
s,u=req("POST",f"{A}/auth/register",{"name":"Usuário E2E","email":EMAIL,"password":"senha-forte"}); check("usuário cadastrado (201)",s==201,s)
s,_=req("POST",f"{A}/auth/register",{"name":"Usuário E2E","email":EMAIL.upper(),"password":"senha-forte"}); check("e-mail repetido -> 409",s==409,s)
s,_=req("POST",f"{A}/auth/login",{"email":EMAIL,"password":"senha-errada"}); check("senha errada -> 401",s==401,s)
s,login=req("POST",f"{A}/auth/login",{"email":EMAIL,"password":"senha-forte"}); check("login devolve token (200)",s==200 and bool(login.get("token")),s)
TOKEN=login["token"]
s,me=req("GET",f"{A}/auth/me"); check("/auth/me devolve o dono do token",s==200 and me["email"]==EMAIL,me)
s,_=req("GET",f"{O}/orders",auth=False); check("order-service sem token -> 401",s==401,s)
s,_=req("GET",f"{S}/delivery-slots?from={D}&to={D}",auth=False); check("scheduling-service sem token -> 401",s==401,s)
s,_=req("POST",f"{S}/delivery-slots",{"state":"SP","date":D,"period":"MORNING","capacity":1}); check("janela SP manhã criada (201)",s==201)
s,_=req("POST",f"{S}/delivery-slots",{"state":"SP","date":D,"period":"AFTERNOON","capacity":1}); check("janela SP tarde criada (201)",s==201)
s,_=req("POST",f"{S}/delivery-slots",{"state":"SP","date":D,"period":"MORNING","capacity":5}); check("janela duplicada -> 409",s==409,s)
s,a=req("POST",f"{O}/orders",order("SP",D,"Cliente A")); check("pedido A criado (201)",s==201)
a=wait(a["id"]); check("pedido A SCHEDULED de manhã",a["status"]=="SCHEDULED" and a["scheduledDelivery"]["period"]=="MORNING",a["status"])
s,b=req("POST",f"{O}/orders",order("SP",D,"Cliente B")); b=wait(b["id"])
check("pedido B SCHEDULED à tarde",b["status"]=="SCHEDULED" and b["scheduledDelivery"]["period"]=="AFTERNOON",b["status"])
s,c=req("POST",f"{O}/orders",order("SP",D,"Cliente C")); c=wait(c["id"])
check("pedido C NOT_SCHEDULED (janelas lotadas)",c["status"]=="NOT_SCHEDULED",c.get("reason"))
s,_=req("POST",f"{O}/orders/{a['id']}/cancel",{"reason":"Cliente desistiu"}); check("cancela A (200)",s==200)
for _ in range(40):
    _,slots=req("GET",f"{S}/delivery-slots?state=SP&from={D}&to={D}")
    morning=[x for x in slots if x["period"]=="MORNING"][0]
    if morning["booked"]==0: break
    time.sleep(0.5)
check("vaga da manhã liberada após cancelar A",morning["booked"]==0,morning)
_,sch=req("GET",f"{S}/schedulings/{a['id']}"); check("agendamento de A CANCELED",sch["status"]=="CANCELED")
s,d=req("POST",f"{O}/orders",order("SP",D,"Cliente D")); d=wait(d["id"])
check("pedido D reaproveita a vaga liberada (manhã)",d["status"]=="SCHEDULED" and d["scheduledDelivery"]["period"]=="MORNING",d["status"])
s,_=req("POST",f"{O}/orders/{a['id']}/cancel",{"reason":"de novo"}); check("cancela A de novo -> 422",s==422,s)
req("POST",f"{S}/delivery-slots",{"state":"RJ","date":D,"period":"MORNING","capacity":2})
with cf.ThreadPoolExecutor(10) as ex:
    ids=list(ex.map(lambda i: req("POST",f"{O}/orders",order("RJ",D,f"Concorrente {i}"))[1]["id"], range(10)))
final=[wait(i) for i in ids]
scheduled=sum(f["status"]=="SCHEDULED" for f in final); not_scheduled=sum(f["status"]=="NOT_SCHEDULED" for f in final)
_,rj=req("GET",f"{S}/delivery-slots?state=RJ&from={D}&to={D}")
check("concorrência: 10 pedidos para 2 vagas -> 2 agendados, 8 sem agenda",scheduled==2 and not_scheduled==8,f"scheduled={scheduled} not_scheduled={not_scheduled}")
check("concorrência: janela RJ não estourou (booked=2)",rj[0]["booked"]==2,rj[0]["booked"])
s,e=req("POST",f"{O}/orders",{"customer":"","address":{"street":"R","number":"1","city":"X","state":"sp","zipCode":"1"},"items":[],"desiredDeliveryDate":"2000-01-01"})
fields=sorted({f["field"] for f in e.get("fieldErrors",[])}); check("pedido inválido -> 400 com erros por campo",s==400 and e.get("code")=="BAD_REQUEST" and len(fields)>=4,fields)
s,_=req("GET",f"{O}/orders/does-not-exist"); check("pedido inexistente -> 404",s==404)
s,page=req("GET",f"{O}/orders?status=SCHEDULED"); check("lista por status SCHEDULED",s==200 and page["totalElements"]==4,page.get("totalElements"))
print(f"\nResultado: {passed} ok, {failed} com falha")
