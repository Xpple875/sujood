python -c "import ssl,socket,hashlib,base64; ctx=ssl.create_default_context(); s=ctx.wrap_socket(socket.socket(), server_hostname='api.aladhan.com'); s.connect(('api.aladhan.com',443)); cert=s.getpeercert(binary_form=True); print(base64.b64encode(hashlib.sha256(ssl.DER_cert_to_PEM_cert(cert).encode()).digest()).decode())"
```

**Step 2 — It'll spit out a string** like `abc123xyz.../something==`

**Step 3 — Open these two files** and replace `PLACEHOLDER_RUN_PIN_COMMAND_ON_YOUR_MACHINE` with that string in both:
- `Sujood/app/src/main/java/com/sujood/app/data/api/RetrofitClient.kt`
- `Sujood/app/src/main/res/xml/network_security_config.xml`

**Step 4 — Commit it:**
```
git add .
git commit -m "security: add real cert pin for aladhan API"
git push
