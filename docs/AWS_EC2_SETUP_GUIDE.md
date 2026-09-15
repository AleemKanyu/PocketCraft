# PocketHost AWS EC2 Setup Guide (Simplified)

A fast, streamlined step-by-step guide to set up an AWS EC2 instance for hosting the **PocketHost Relay Coordinator** and **Subdomain Router**.

---

## 📋 Quick Summary of What to Add

### 1. AWS Resources
- **EC2 Instance**: Ubuntu 24.04 LTS (`t3.medium` or `t4g.medium`, 25 GB gp3 disk).
- **Elastic IP**: 1 Static Public IPv4 address attached to your instance.
- **Security Group (Firewall)**:

| Port | Protocol | Source | Purpose |
| :--- | :--- | :--- | :--- |
| **22** | TCP | Your IP | SSH Server Access |
| **80** | TCP | `0.0.0.0/0` | HTTP / Let's Encrypt SSL |
| **443** | TCP | `0.0.0.0/0` | HTTPS Secure Web |
| **8080** | TCP | `0.0.0.0/0` | Relay Control API (`CONTROL_PORT`) |
| **9000** | TCP | `0.0.0.0/0` | Android App Tunnel Pool (`PHONE_RELAY_PORT`) |
| **25565** | TCP | `0.0.0.0/0` | Minecraft Java Default Port (Subdomain router) |
| **25500 - 35500** | TCP | `0.0.0.0/0` | Java Dynamic Player Connections |
| **25500 - 35500** | UDP | `0.0.0.0/0` | Bedrock Dynamic Player Connections (RakNet) |

---

## 🚀 Step 1: AWS Web Console Setup

1. **Launch Instance**:
   - Go to [AWS EC2 Console](https://console.aws.amazon.com/ec2/) > **Launch an instance**.
   - Name: `pockethost-relay`.
   - OS: **Ubuntu Server 24.04 LTS**.
   - Instance Type: **t3.medium** (recommended) or **t4g.medium** (AWS Graviton ARM64).
   - Key Pair: Select or create a `.pem` key pair (save it to your local machine).
   - Storage: Change root disk to **25 GiB** (`gp3`).

2. **Configure Security Group**:
   - Create a new security group and add the rules from the table above (make sure to add **both TCP and UDP** for `25500-35500`).

3. **Attach Elastic IP (Static IP)**:
   - In left menu: **Network & Security** > **Elastic IPs** > **Allocate Elastic IP address**.
   - Select your new IP > **Actions** > **Associate Elastic IP address** > pick your EC2 instance.
   - Point your DNS domain (e.g. `*.pocketcraft.online` and `mine.pocketcraft.online`) to this Elastic IP.

---

## 💻 Step 2: Connect to Your Server

Open terminal on your computer:
```bash
chmod 400 your-key.pem
ssh -i "your-key.pem" ubuntu@<YOUR_ELASTIC_IP>
```

---

## ⚙️ Step 3: Run the 1-Step Setup Script

Run this block of commands to update Ubuntu, configure firewall, install Node.js 20, PM2, and optimize socket limits:

```bash
# 1. Update OS & tools
sudo apt update && sudo apt upgrade -y
sudo apt install -y git curl wget unzip htop ufw fail2ban build-essential

# 2. Install Node.js 20 LTS & PM2
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs
sudo npm install -g pm2

# 3. Configure Local Firewall (UFW)
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 8080/tcp
sudo ufw allow 9000/tcp
sudo ufw allow 25565/tcp
sudo ufw allow 25500:35500/tcp
sudo ufw allow 25500:35500/udp
echo "y" | sudo ufw enable

# 4. Tune Socket Limits (prevents "too many open files" with many players)
sudo bash -c 'cat >> /etc/security/limits.conf <<EOF
* soft nofile 65535
* hard nofile 65535
root soft nofile 65535
root hard nofile 65535
ubuntu soft nofile 65535
ubuntu hard nofile 65535
EOF'

sudo bash -c 'cat >> /etc/sysctl.conf <<EOF
fs.file-max = 2097152
net.core.somaxconn = 4096
net.ipv4.tcp_max_syn_backlog = 4096
net.ipv4.ip_local_port_range = 1024 65535
EOF'

sudo sysctl -p
```

---

## 📦 Step 4: Deploy the Relay

1. **Clone your repository**:
   ```bash
   git clone https://github.com/AleemKanyu/PocketCraft_.git
   cd PocketCraft_/relay
   npm install --production
   ```

2. **Create the PM2 ecosystem config**:
   ```bash
   cat << 'EOF2' > ecosystem.config.js
   module.exports = {
     apps: [
       {
         name: 'pocket-relay',
         script: 'index.js',
         env: {
           NODE_ENV: 'production',
           PUBLIC_IP: 'YOUR_ELASTIC_IP_HERE',
           RELAY_SECRET: 'YOUR_SHARED_SECRET_HERE',
           PORT_POOL_START: 25500,
           PORT_POOL_END: 35500
         }
       },
       {
         name: 'pocket-subdomain-router',
         script: 'subdomain-listener.js',
         env: {
           NODE_ENV: 'production',
           SUBDOMAIN_LISTEN_PORT: 25565,
           CONTROL_API_BASE: 'http://127.0.0.1:8080',
           SUBDOMAIN_BASE_DOMAIN: 'pocketcraft.online'
         }
       }
     ]
   };
EOF2
   ```
   *(Edit `ecosystem.config.js` with your real Elastic IP and relay secret using `nano ecosystem.config.js`).*

3. **Start the services and enable boot persistence**:
   ```bash
   pm2 start ecosystem.config.js
   pm2 save
   pm2 startup
   ```
   *(Run the command that PM2 prints out to register it as a systemd service).*

---

## 🔍 Step 5: Verification & Maintenance

- **Check status**:
  ```bash
  pm2 status
  ```
- **View live logs**:
  ```bash
  pm2 logs pocket-relay
  # or
  pm2 logs pocket-subdomain-router
  ```
- **Restart services**:
  ```bash
  pm2 restart all
  ```
- **Test control API**:
  ```bash
  curl http://127.0.0.1:8080/status
  ```
