import json, time, urllib.request, urllib.error, concurrent.futures as cf
import os
O=os.environ.get("ORDER_URL","http://localhost:8081"); S=os.environ.get("SCHEDULING_URL","http://localhost:8082")
def req(method,url,body=None):
    r=urllib.request.Request(url,method=method,data=json.dumps(body).encode() if body is not None else None,headers={"Content-Type":"application/json"})
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
    passed+=cond; failed+=(not cond); print(("OK   " if cond else "FAIL ")+name+(f"  [{extra}]" if extra else ""))
D="2026-12-10"
s,_=req("POST",f"{S}/delivery-slots",{"state":"SP","date":D,"period":"MORNING","capacity":1}); check("SP morning slot created (201)",s==201)
s,_=req("POST",f"{S}/delivery-slots",{"state":"SP","date":D,"period":"AFTERNOON","capacity":1}); check("SP afternoon slot created (201)",s==201)
s,_=req("POST",f"{S}/delivery-slots",{"state":"SP","date":D,"period":"MORNING","capacity":5}); check("duplicated slot -> 409",s==409,s)
s,a=req("POST",f"{O}/orders",order("SP",D,"Cliente A")); check("order A created (201)",s==201)
a=wait(a["id"]); check("order A SCHEDULED in the morning",a["status"]=="SCHEDULED" and a["scheduledDelivery"]["period"]=="MORNING",a["status"])
s,b=req("POST",f"{O}/orders",order("SP",D,"Cliente B")); b=wait(b["id"])
check("order B SCHEDULED in the afternoon",b["status"]=="SCHEDULED" and b["scheduledDelivery"]["period"]=="AFTERNOON",b["status"])
s,c=req("POST",f"{O}/orders",order("SP",D,"Cliente C")); c=wait(c["id"])
check("order C NOT_SCHEDULED (slots full)",c["status"]=="NOT_SCHEDULED",c.get("reason"))
s,_=req("POST",f"{O}/orders/{a['id']}/cancel",{"reason":"Cliente desistiu"}); check("cancel A (200)",s==200)
for _ in range(40):
    _,slots=req("GET",f"{S}/delivery-slots?state=SP&from={D}&to={D}")
    morning=[x for x in slots if x["period"]=="MORNING"][0]
    if morning["booked"]==0: break
    time.sleep(0.5)
check("morning slot released after canceling A",morning["booked"]==0,morning)
_,sch=req("GET",f"{S}/schedulings/{a['id']}"); check("scheduling of A CANCELED",sch["status"]=="CANCELED")
s,d=req("POST",f"{O}/orders",order("SP",D,"Cliente D")); d=wait(d["id"])
check("order D reuses the released slot (morning)",d["status"]=="SCHEDULED" and d["scheduledDelivery"]["period"]=="MORNING",d["status"])
s,_=req("POST",f"{O}/orders/{a['id']}/cancel",{"reason":"de novo"}); check("cancel A again -> 422",s==422,s)
req("POST",f"{S}/delivery-slots",{"state":"RJ","date":D,"period":"MORNING","capacity":2})
with cf.ThreadPoolExecutor(10) as ex:
    ids=list(ex.map(lambda i: req("POST",f"{O}/orders",order("RJ",D,f"Concorrente {i}"))[1]["id"], range(10)))
final=[wait(i) for i in ids]
scheduled=sum(f["status"]=="SCHEDULED" for f in final); not_scheduled=sum(f["status"]=="NOT_SCHEDULED" for f in final)
_,rj=req("GET",f"{S}/delivery-slots?state=RJ&from={D}&to={D}")
check("concurrency: 10 orders for 2 spots -> 2 scheduled, 8 not scheduled",scheduled==2 and not_scheduled==8,f"scheduled={scheduled} not_scheduled={not_scheduled}")
check("concurrency: RJ slot did not overflow (booked=2)",rj[0]["booked"]==2,rj[0]["booked"])
s,e=req("POST",f"{O}/orders",{"customer":"","address":{"street":"R","number":"1","city":"X","state":"sp","zipCode":"1"},"items":[],"desiredDeliveryDate":"2000-01-01"})
fields=sorted({f["field"] for f in e.get("fieldErrors",[])}); check("invalid order -> 400 with field errors",s==400 and e.get("code")=="BAD_REQUEST" and len(fields)>=4,fields)
s,_=req("GET",f"{O}/orders/does-not-exist"); check("missing order -> 404",s==404)
s,page=req("GET",f"{O}/orders?status=SCHEDULED"); check("list by status SCHEDULED",s==200 and page["totalElements"]==4,page.get("totalElements"))
print(f"\nResult: {passed} passed, {failed} failed")
