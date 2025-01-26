
# Instruções para Configuração do Servidor 🚀

## Passos para subir o servidor no Ubuntu 24 🐧

### 1. Clonar o Repositório

Clone o repositório na pasta `/opt`:
```bash
sudo git clone [URL_DO_REPOSITÓRIO] /opt/[NOME_DO_PROJETO]
```
Substitua `[URL_DO_REPOSITÓRIO]` pela URL do seu repositório e `[NOME_DO_PROJETO]` pelo nome do seu projeto.

### 2. Iniciar o Docker Compose

Na raiz do projeto, inicie o `docker-compose`:
```bash
cd /opt/[NOME_DO_PROJETO]
sudo docker-compose up -d
```

### 3. Executar o Script `convert_and_execute`

Ainda na raiz do projeto, execute o script `convert_and_execute` para iniciar o banco de dados, importar as tabelas e iniciar o login server:
```bash
./convert_and_execute
```

### 4. Iniciar o Game Server

Na raiz, procure pela pasta do game server, entre na pasta e execute o script `startGameServer.sh`:
```bash
cd game_server
./startGameServer.sh
```

### 🎮 Pronto, é só jogar!

Se tudo estiver configurado corretamente, seu servidor estará pronto para uso. Aproveite o jogo!
