const fs = require('fs');

function ensureTopLevelYamlValue(original, key, value) {
    const lines = (original || "").split('\n');
    const keyIndex = lines.findIndex(line => line.trimStart().startsWith(key + ":") && !line.startsWith(" ") && !line.startsWith("\t"));
    if (keyIndex !== -1) {
        lines[keyIndex] = key + ": " + value;
    } else {
        lines.push("");
        lines.push(key + ": " + value);
    }
    return lines.join("\n").trimEnd() + "\n";
}

function ensureYamlSectionValue(original, section, key, value) {
    const lines = (original || "").split('\n');
    let sectionStart = lines.findIndex(l => l.trim() === section + ":");
    if (sectionStart === -1) {
        if (lines.length === 1 && lines[0].trim() === "") lines.pop();
        if (lines.length > 0 && lines[lines.length - 1].trim() !== "") lines.push("");
        lines.push(section + ":");
        lines.push("  " + key + ": " + value);
        return lines.join("\n").trimEnd() + "\n";
    }
    
    let sectionEnd = lines.length;
    for (let index = sectionStart + 1; index < lines.length; index++) {
        const line = lines[index];
        const trimmed = line.trim();
        if (trimmed === "" || trimmed.startsWith("#")) continue;
        if (!line.startsWith(" ") && !line.startsWith("\t")) {
            sectionEnd = index;
            break;
        }
    }
    
    const keyIndex = lines.slice(sectionStart + 1, sectionEnd).findIndex(line => 
        (line.startsWith(" ") || line.startsWith("\t")) && line.trimStart().startsWith(key + ":")
    );
    
    if (keyIndex !== -1) {
        lines[sectionStart + 1 + keyIndex] = "  " + key + ": " + value;
    } else {
        lines.splice(sectionEnd, 0, "  " + key + ": " + value);
    }
    return lines.join("\n").trimEnd() + "\n";
}

let original = fs.readFileSync('test_device_config.yml', 'utf8');
let updated = original;

updated = ensureYamlSectionValue(updated, "bedrock", "address", "0.0.0.0");
updated = ensureYamlSectionValue(updated, "bedrock", "port", "19132");
updated = ensureYamlSectionValue(updated, "bedrock", "clone-remote-port", "false");
updated = ensureYamlSectionValue(updated, "bedrock", "broadcast-port", "19132");
updated = ensureYamlSectionValue(updated, "bedrock", "enable-proxy-protocol", "false");
updated = ensureYamlSectionValue(updated, "bedrock", "motd1", "PocketCraft Server");
updated = ensureYamlSectionValue(updated, "bedrock", "motd2", "Tap to join");
updated = ensureTopLevelYamlValue(updated, "ping-passthrough-interval", "1");
updated = ensureTopLevelYamlValue(updated, "async-motd", "false");
updated = ensureTopLevelYamlValue(updated, "cache-chunks", "true");
updated = ensureTopLevelYamlValue(updated, "max-auto-connect-attempts", "5");
updated = ensureTopLevelYamlValue(updated, "show-cooldown", "disabled");
updated = ensureTopLevelYamlValue(updated, "forward-hostname", "false");
updated = ensureTopLevelYamlValue(updated, "floodgate-key-file", "../floodgate/key.pem");

updated = ensureYamlSectionValue(updated, "java", "auth-type", "floodgate");
updated = ensureYamlSectionValue(updated, "remote", "address", "127.0.0.1");
updated = ensureYamlSectionValue(updated, "remote", "port", "25565");
updated = ensureYamlSectionValue(updated, "remote", "auth-type", "floodgate");

updated = ensureTopLevelYamlValue(updated, "passthrough-motd", "false");
updated = ensureTopLevelYamlValue(updated, "passthrough-player-counts", "false");

if (updated !== original) {
    console.log("CHANGES DETECTED");
    // Print the difference around auth-type
    const idx = updated.indexOf("auth-type:");
    console.log(updated.substring(idx - 100, idx + 100));
} else {
    console.log("NO CHANGES DETECTED");
}
