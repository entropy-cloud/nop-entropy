
The prompt used in the [Pythagora-io/GPT-Pilot](https://github.com/Pythagora-io/gpt-pilot) project is as follows:

---


```python
{
    "model": "gpt-3",
    "messages": [
        {"role": "system", "content": "You are a highly intelligent assistant with knowledge of everything up to 2023. You are empathetic, polite, and follow all ethical guidelines."},
        {
            "role": "user",
            "content": "Given the following text:[text], generate a detailed response using the GPT-3 model."
        },
        {"role": "assistant", "content": "[generated text]"}
    ]
}
```


```python

import requests

def generate_response(prompt):
    url = "https://api.openai.com/v1/chat/completions"
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {your_api_key}"
    }
    
    payload = {
        "model": "gpt-3",
        "messages": [
            {"role": "system", "content": "You are a highly intelligent assistant with knowledge of everything up to 2023. You are empathetic, polite, and follow all ethical guidelines."},
            {
                "role": "user",
                "content": f"Given the following text:{prompt}, generate a detailed response using the GPT-3 model."
            },
            {"role": "assistant", "content": "[generated text]"}
        ]
    }
    
    response = requests.post(url, headers=headers, json=payload)
    return response.json()
```

---

