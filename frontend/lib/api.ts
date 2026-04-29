const BASE_URL = "http://localhost:8080";

export interface Chat {
  id: string;
  title: string;
  updatedAt: string;
}

export interface Folder {
  id: string;
  name: string;
  createdAt: string;
}

export interface FileItem {
  id: string;
  name: string;
  type: string;
  url: string; 
}

export interface Message {
  id: string;
  role: "user" | "assistant";
  content: string;
  sources?: Source[];
}

export interface Source {
  path: string;
  fileName: string;
  score: number;
  base64?: string;
  type: "PDF" | "TEXT" | "IMAGE" | "OTHER";
}



export async function getChats(): Promise<Chat[]> {
  const response = await fetch(`${BASE_URL}/api/chat/all`);
  if (!response.ok) throw new Error("Failed to fetch chats");
  const chatIds: string[] = await response.json();
  
  
  return chatIds.map(id => ({
    id,
    title: `Chat ${id.slice(0, 8)}`,
    updatedAt: new Date().toISOString()
  })).reverse(); 
}

export async function createChat(): Promise<Chat> {
  const response = await fetch(`${BASE_URL}/api/chat/create`, {
    method: "POST"
  });
  if (!response.ok) throw new Error("Failed to create chat");
  const data = await response.json();
  const id = data.id;
  
  return {
    id,
    title: `Nowa konwersacja`,
    updatedAt: new Date().toISOString()
  };
}

export async function getFolders(): Promise<Folder[]> {
  const response = await fetch(`${BASE_URL}/api/folders`);
  if (!response.ok) throw new Error("Failed to fetch folders");
  const folders: any[] = await response.json();
  
  return folders.map(f => ({
    id: f.id,
    name: f.name,
    createdAt: new Date().toISOString() 
  }));
}

export async function createFolder(name: string): Promise<Folder> {
  const response = await fetch(`${BASE_URL}/api/folders/create`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ name })
  });
  if (!response.ok) throw new Error("Failed to create folder");
  const folder = await response.json();
  
  return {
    id: folder.id,
    name: folder.name,
    createdAt: new Date().toISOString()
  };
}

export async function deleteFolder(id: string): Promise<void> {
  const response = await fetch(`${BASE_URL}/api/folders/${id}`, {
    method: "DELETE"
  });
  if (!response.ok) throw new Error("Failed to delete folder");
}

export async function getAllFiles(): Promise<FileItem[]> {
  const response = await fetch(`${BASE_URL}/api/data/files`);
  if (!response.ok) throw new Error("Failed to fetch files");
  const allFiles: any[] = await response.json();
  
  return allFiles.map(f => ({
    id: f.path,
    name: f.name,
    type: f.fileType,
    url: f.imageBase64 ? `data:${f.fileType};base64,${f.imageBase64}` : ""
  }));
}

export async function getFilesInFolder(folderName: string): Promise<FileItem[]> {
  const response = await fetch(`${BASE_URL}/api/data/files`);
  if (!response.ok) throw new Error("Failed to fetch files");
  const allFiles: any[] = await response.json();
  
  
  const folderPrefix = `dir://${folderName}/`;
  return allFiles
    .filter(f => f.path.startsWith(folderPrefix))
    .map(f => ({
      id: f.path,
      name: f.name,
      type: f.fileType,
      url: f.imageBase64 ? `data:${f.fileType};base64,${f.imageBase64}` : ""
    }));
}

export async function uploadFileToFolder(folderId: string, file: File): Promise<void> {
  const formData = new FormData();
  formData.append("file", file);
  
  const response = await fetch(`${BASE_URL}/api/folders/${folderId}/upload`, {
    method: "POST",
    body: formData
  });
  
  if (!response.ok) throw new Error("Failed to upload file");
}

export async function renameFile(oldPath: string, newName: string): Promise<void> {
  const response = await fetch(`${BASE_URL}/api/data/files/rename`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ oldPath, newName })
  });
  
  if (!response.ok) throw new Error("Failed to rename file");
}

export async function moveFiles(filePaths: string[], targetFolderId: string): Promise<void> {
  const response = await fetch(`${BASE_URL}/api/data/files/move`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ filePaths, targetFolderId })
  });
  
  if (!response.ok) throw new Error("Failed to move files");
}

export async function getMessagesForChat(chatId: string): Promise<Message[]> {
  const response = await fetch(`${BASE_URL}/api/chat/${chatId}/messages`);
  if (!response.ok) throw new Error("Failed to fetch messages");
  const messages: any[] = await response.json();
  
  return messages.map((m, index) => ({
    id: `${chatId}-${index}`,
    role: m.type === "USER" ? "user" : "assistant",
    content: m.text,
    sources: m.sources
  }));
}

export async function sendMessage(chatId: string, content: string): Promise<Message> {
  const response = await fetch(`${BASE_URL}/api/chat/${chatId}/send`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ message: content })
  });
  
  if (!response.ok) throw new Error("Failed to send message");
  const data = await response.json();
  
  return {
    id: `resp-${Date.now()}`,
    role: "assistant",
    content: data.response,
    sources: data.sources
  };
}

export async function renameChat(chatId: string, newName: string): Promise<void> {
  const response = await fetch(`${BASE_URL}/api/chat/${chatId}/rename`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ newName })
  });
  
  if (!response.ok) throw new Error("Failed to rename chat");
}

export async function deleteChat(chatId: string): Promise<void> {
  const response = await fetch(`${BASE_URL}/api/chat/${chatId}`, {
    method: "DELETE"
  });
  
  if (!response.ok) throw new Error("Failed to delete chat");
}
